package com.ericom.jenkins.test

import hudson.AbortException
import java.net.Proxy
import java.util.Scanner

/**
 * Created by lev on 6/12/17.
 */
class TestFlow implements Serializable{
    def steps
    def config
    def runSystemScript

    TestFlow(steps, config) {
        this.steps = steps
        this.config = config
    }


    def downloadTestFiles() {
        for(int i = 0; i < this.config['test']['swarm']['files'].size(); i++) {
            def file = this.config['test']['swarm']['files'][i]
            def url = this.config['test']['swarm']['repo'] + '/' + file
            this.steps.sh "curl ${url} > ${file}"
            if(file.endsWith('.sh')) {
                this.runSystemScript = file
                this.steps.sh "chmod +x ${file}"
            }
        }

        this.steps.sh 'ls -al'
    }

    private streamToSring(InputStream input) {
        def s = new Scanner(input);
        def builder = new StringBuilder();
        while (s.hasNextLine()) {
            builder.append(s.nextLine() +"\n");
        }
        return builder.toString();
    }

    def tryToClearEnvironment() {
        def result
        def stop = false
        while(!stop) {
            try {
                result = this.steps.sh script:'(docker swarm leave -f) 2>&1', returnStdout:true
            } catch (AbortException e) {
                this.steps.echo result
                return
            }

            stop = true
        }

    }

    def testSystemIsUp() {

    }

    def run() {
        try {
            this.steps.stage("Clean Environment") {
                this.tryToClearEnvironment()
            }

            this.steps.stage("Prepare Test") {
                this.downloadTestFiles()
            }

            this.steps.stage("Setup system") {
                this.steps.sh script:"./${this.runSystemScript}"
            }

            this.steps.stage('Test System UP') {
                int counter = 1
                int max_retries = this.config['test']['wait']['retries']

                while (counter <= max_retries) {
                    this.steps.echo "Going test system Retry: ${counter}"
                    try {
                        def proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(this.config['test']['proxy']['address'],this.config['test']['proxy']['port'].toInteger()))
                        for (int i = 0; i < this.config['test']['urls'].size(); i++) {
                            def url = new URL(this.config['test']['urls'][i]).openConnection(proxy)
                            def result = streamToSring(url.getInputStream())

                            if(!result.contains('Protected by Ericom Shield')) {
                                throw new Exception('Page is not AccessNow')
                            }
                        }
                        break
                    } catch (Exception e) {
                        this.steps.echo e.toString()
                    }
                    sleep(this.config['test']['wait']['sleep'].toInteger() * 1000)
                    counter++
                }

                if(counter > max_retries) {
                    this.steps.echo 'Maximum retries exceeded'
                    throw new Exception('Maximum retries exceeded')
                }
            }
        } finally {
            this.steps.stage("Final Clean") {
                this.tryToClearEnvironment()
            }
        }


    }

}
