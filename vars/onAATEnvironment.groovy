import uk.gov.hmcts.contino.Environment

def call(String environment, Closure block) {
  if (environment == new Environment(env).nonProd) {
    return block.call()
  }
}
