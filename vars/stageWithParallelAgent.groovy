/**
 * Stage with Agent selection. Stages run in parallel on separate agents
 * if docker containers are used. Otherwise in parallel on the same vm agent.
 *
 */
def call(String name, String product, Map<String, Closure> bodies, boolean failFast) {
  stage(name) {
//    withParallelDockerAgent(product, bodies, failFast) {
//      body()
//    }

  }
}