def call(Map configMap){
    pipeline {
        agent {
            label 'AGENT-1'
        }
        options {
            timeout(time: 30, unit: 'MINUTES') 
            disableConcurrentBuilds()
            ansiColor('xterm')
        }
        parameters {
            booleanParam(name: 'Deploy', defaultValue: false, description: 'Toggle this value')
        }
        
        environment {
            def appVersion = ''
            nexusUrl = pipelineGlobals.nexusUrl()
            region = pipelineGlobals.region()
            account_id = '798283511816'
            component = configMap.get("component")
            project = configMap.get("project")
            def releaseExists = ""


        }
        stages {
            stage('read the version') {
                steps {
                    script {
                        echo sh(returnStdout: true, script: 'env')
                        def packagejson = readJSON file: 'package.json'
                        appVersion = packagejson.version
                        echo "application version : $appVersion"

                    }
                    
                }
            }
            stage('install depenecies') {
                steps {
                    sh """
                    npm install
                    ls -ltr
                    echo "application version : $appVersion"
                    """
        
                }
            }
            stage('build') {
                steps {
                    sh """
                    
                    zip -q -r ${component}-${appVersion}.zip * -x Jenkinsfile -x ${component}-${appVersion}.zip
                    ls -ltr
                    """
                    
                }
            }

            stage('docker build'){
                steps{
                    sh """
                        aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com

                        docker build -t ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion} .

                        docker push ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion}
                    """
                }
            }

            stage('deploy'){
                steps{
                    script{
                        releaseExists = sh(script: "helm list -A --short | grep -w ${component} || true", returnStdout: true).trim()
                        if(releaseExists.isEmpty()){
                            echo "${component} not installed yet, first time installation"
                            sh"""
                                aws eks update-kubeconfig --region ${region} --name ${project}-dev
                                cd helm
                                sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                                helm install ${component} -n ${project} .
                            """
                        }
                        else{
                            echo "${component} exists, running upgrade"
                            sh"""
                                aws eks update-kubeconfig --region ${region} --name ${project}-dev
                                cd helm
                                sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                                helm upgrade ${component} -n ${project} .
                            """
                        }
                    }
                }

                stage('Verify Deployment'){
                steps{
                    script{
                        rollbackStatus = sh(script: "kubectl rollout status deployment/backend -n ${project} --timeout=1m || true", returnStdout: true).trim()
                        if(rollbackStatus.contains('successfully rolled out')){
                            echo "Deployment is successfull"
                        }
                        else{
                            echo "Deployment is failed, performing rollback"
                            if(releaseExists.isEmpty()){
                                error "Deployment failed, not able to rollback, since it is first time deployment"
                            }
                            else{
                                sh """
                                aws eks update-kubeconfig --region ${region} --name ${project}-dev
                                helm rollback backend -n ${project} 0
                                sleep 60
                                """
                                rollbackStatus = sh(script: "kubectl rollout status deployment/backend -n expense --timeout=2m || true", returnStdout: true).trim()
                                if(rollbackStatus.contains('successfully rolled out')){
                                    error "Deployment is failed, Rollback is successfull"
                                }
                                else{
                                    error "Deployment is failed, Rollback is failed"
                                }
                            }
                        }
                    }
                }
            }



            /* stage('Sonar Scan'){
                environment {
                    scannerHome = tool 'sonar' //referring scanner CLI
                }
                steps {
                    script {
                        withSonarQubeEnv('sonar') { //referring sonar server
                            sh "${scannerHome}/bin/sonar-scanner"
                        }
                    }
                }
            }
    */

            /* stage('Nexus Artifact Upload'){
                steps{
                    script{
                        nexusArtifactUploader(
                            nexusVersion: 'nexus3',
                            protocol: 'http',
                            nexusUrl: "${nexusUrl}",
                            groupId: 'com.expense',
                            version: "${appVersion}",
                            repository: "backend",
                            credentialsId: 'nexus-auth',
                            artifacts: [
                                [artifactId: "backend" ,
                                classifier: '',
                                file: "backend-" + "${appVersion}" + '.zip',
                                type: 'zip']
                            ]
                        )
                    }
                }
            } */
            /* stage("Quality Gate") {
                steps {
                timeout(time: 30, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
                }
            } */

            /* stage('Deploy'){
                when {
                    expression {
                        parameters.deploy 
                    }
                }
                steps{
                    script{
                        def params = [
                            string(name: 'appVersion', value: "${appVersion}")
                        ]
                        build job: 'backend-deploy', parameters: params, wait: false
                    }
                }
            } */


            
            
            
        }

        post { 
            always { 
                echo 'I will always say Hello again!'
            }
            success { 
                echo 'I will run when pipeline is success'
            }
            failure { 
                echo 'I will run when pipeline is failure'
            }
        }
    }
}