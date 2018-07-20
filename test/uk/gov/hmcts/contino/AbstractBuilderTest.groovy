package uk.gov.hmcts.contino

import spock.lang.Specification

class AbstractBuilderTest extends Specification {

  def builder
  def mockSteps
  def mockGatling

  def setup() {
    mockSteps = Mock(JenkinsStepMock.class)
    mockGatling = Mock(Gatling)
    builder = new BuilderImpl(mockSteps)
    builder.gatling = mockGatling
  }

  def "performanceTest calls 'gatling.execute()'"() {

    when:
      builder.performanceTest()
    then:
      1 * mockGatling.execute()
  }

  class BuilderImpl extends AbstractBuilder {
    BuilderImpl(steps) {
      super(steps)
    }

    @Override
    def build() {
      return null
    }

    @Override
    def test() {
      return null
    }

    @Override
    def sonarScan() {
      return null
    }

    @Override
    def smokeTest() {
      return null
    }

    @Override
    def functionalTest() {
      return null
    }

    @Override
    def apiGatewayTest() {
      return null
    }

    @Override
    def securityCheck() {
      return null
    }

    @Override
    def crossBrowserTest(){
      return null
    }

    @Override
    def addVersionInfo() {
      return null
    }
  }
}
