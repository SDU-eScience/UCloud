Scenario: login with invalid credentials
Given I am on the login page
When I fill in "Username" with "picawrong"
And I fill in "Password" with "picawrong"
And I press "Login"
Then I should be on error page
And I should see "Login not successful"