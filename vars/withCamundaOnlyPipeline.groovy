import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.Builder

def call(type, String product, String component, Closure body) {

  Subscription subscription = new Subscription(env)
  Environment environment = new Environment(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "", subscription.prodName)
  String agentType = env.BUILD_AGENT_TYPE

  def pipelineConfig = new AppPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbackRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new AppPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  dsl.onStageFailure {
    currentBuild.result = 'FAILURE'
  }

  node(agentType) {
    def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
    try {
      dockerAgentSetup()
      env.PATH = "$env.PATH:/usr/local/bin"

      PipelineCallbacksRunner pcr = callbacksRunner
      AppPipelineConfig config = pipelineConfig
      Builder builder = pipelineType.builder

      stageWithAgent('Checkout', product) {
        pcr.callAround('checkout') {
          checkoutScm()
        }
      }

      parallel(
        'Unit tests and Sonar scan': {
          pcr.callAround('test') {
            timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'test') {
              builder.test()
            }
          }

          pcr.callAround('sonarscan') {
            pluginActive('sonar') {
              withSonarQubeEnv("SonarQube") {
                builder.sonarScan()
              }

              timeoutWithMsg(time: 30, unit: 'MINUTES', action: 'Sonar Scan') {
                def qg = waitForQualityGate()
                if (qg.status != 'OK') {
                  error "Pipeline aborted due to quality gate failure: ${qg.status}"
                }
              }
            }
          }
        },

        'Security Checks': {
          pcr.callAround('securitychecks') {
            builder.securityCheck()
          }
        },

        failFast: true
      )

      // AAT/Demo/ITHC/Perftest camunda upload
      onNonPR {
        camunda_url = "http://camunda-api-${environment}.service.core-compute-${environment}.internal"
        s2s_url = "http://rpe-service-auth-provider-${environment}.service.core-compute-${environment}.internal"

        camundaPublish(config.s2sServiceName, camunda_url, s2s_url, product)
      }

      // Prod camunda promotion
      onMaster {
        camunda_url = 'http://camunda-api-prod.service.core-compute-prod.internal'
        s2s_url = 'http://rpe-service-auth-provider-prod.service.core-compute-prod.internal'

        camundaPublish(config.s2sServiceName, camunda_url, s2s_url, product)
      }

    } catch (err) {
      currentBuild.result = 'FAILURE'
      notifyBuildFailure channel: slackChannel

      callbacksRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      notifyPipelineDeprecations(slackChannel, metricsPublisher)
      if (env.KEEP_DIR_FOR_DEBUGGING != 'true') {
        deleteDir()
      }
    }

    notifyBuildFixed channel: slackChannel

    callbacksRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
