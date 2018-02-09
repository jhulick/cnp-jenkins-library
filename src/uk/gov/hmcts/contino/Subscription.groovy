package uk.gov.hmcts.contino

class Subscription implements Serializable {
  def final nonProdName
  def final prodName

  Subscription(Map<String, String> env) {
    Objects.requireNonNull(env)

    nonProdName = env.NONPROD_SUBSCRIPTION_NAME ?: 'nonprod'
    prodName = env.PROD_SUBSCRIPTION_NAME ?: 'prod'
  }
}