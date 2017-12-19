package dk.sdu.cloud.features.auth

import cucumber.api.java.After
import cucumber.api.java.Before
import cucumber.api.java.en.And
import cucumber.api.java.en.Given
import cucumber.api.java.en.Then
import cucumber.api.java.en.When
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait



class LoginStepDef {

    private var driver: WebDriver? = null

    @Before
    fun before() {
        driver = FirefoxDriver()
    }

    @After
    fun after() {
        driver!!.quit()
    }

    @Given("^I am on the login page$")
    fun iAmOnTheLoginPage() {
        driver?.get("http://localhost:9090/")
    }

    @When("^I fill in username with \"([^\"]*)\"$")
    fun iFillInUsernameWith(username: String) {
        val element = driver?.findElement(By.name("accountName"))!!
        element.sendKeys(username)
    }

    @And("^I fill in password with \"([^\"]*)\"$")
    fun iFillInPasswordWith(password: String) {
        val element = driver?.findElement(By.name("accountPassword"))!!
        element.sendKeys(password)
    }

    @And("^I press \"([^\"]*)\"$")
    fun iPress(button: String) {
        val submitButton = driver?.findElement(By.name("accountName"))!!
        submitButton.submit()
    }

    @Then("^I should be on the \"([^\"]*)\" page$")
    fun iShouldBeOnThePage(page: String) {
        // Wait 2 seconds for redirection
        WebDriverWait(driver, 3).until {
            d -> d!!.title.toLowerCase() == page.toLowerCase() }
    }

    @And("^I should see \"([^\"]*)\"$")
    fun iShouldSee(username: String) {
        val element = driver?.findElement(By.id("nameField"))!!
        if (element.text != "Welcome, $username")
            throw Error("Should contain 'Welcome, $username', content was ${element.text}")
    }
}