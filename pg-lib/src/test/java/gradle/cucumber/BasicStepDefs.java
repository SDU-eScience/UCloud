package  test.java.gradle.cucumber;

import cucumber.api.java.en.When;

public class BasicStepDefs {

    @When("^I run a failing step")
    public void I_run_a_failing_step() throws Throwable {
        new Production().doWork();
    }
}