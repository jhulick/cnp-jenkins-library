#!groovy
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.PipelineCallbacks

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer

  stage("Build Infrastructure - ${environment}") {
    folderExists('infrastructure') {
      withSubscription(subscription) {
        dir('infrastructure') {
          withIlbIp(environment) {
            tfOutput = spinInfra("${product}-${component}", environment, false, subscription)
            scmServiceRegistration(environment)
          }
        }
      }
      if (pl.migrateDb) {
        stage("DB Migration - ${environment}") {
          builder.dbMigrate(tfOutput.vaultName.value)
        }
      }
    }
  }

  stage("Deploy - ${environment} (staging slot)") {
    pl.callAround("deploy:${environment}") {
      deployer.deploy(environment)
      deployer.healthCheck(environment)
    }
  }

  stage("Smoke Test - ${environment} (staging slot)") {
    withEnv(["TEST_URL=${deployer.getServiceUrl(environment)}"]) {
      pl.callAround('smoketest:${environment}') {
        echo "Using TEST_URL: '$TEST_URL'"
        builder.smokeTest()
      }
    }
  }

  onNonProdEnvironment(environment) {
    stage("Functional Test - ${environment} (staging slot)") {
      withEnv(["TEST_URL=${deployer.getServiceUrl(environment)}"]) {
        pl.callAround('functionaltest:${environment}') {
          echo "Using TEST_URL: '$TEST_URL'"
          builder.functionalTest()
        }
      }
    }
  }

  stage("Promote - ${environment} (staging -> production slot)") {
    withSubscription(subscription) {
      sh "az webapp deployment slot swap --name \"${product}-${component}-${environment}\" --resource-group \"${product}-${component}-${environment}\" --slot staging --target-slot production"
    }
  }
}
