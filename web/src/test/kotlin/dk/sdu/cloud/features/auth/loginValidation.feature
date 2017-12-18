Feature: Logging in to SDUCloud
  Logging in should not occur unless both username and password are validated

Scenario: Login with valid credentials
  Given I am on the login page
  When I fill in username with "pica"
  And I fill in password with "pica"
  And I press "login"
  Then I should be on the "eScienceCloud - Dashboard" page
  And I should see "pica"

Scenario: Login with valid credentials
 Given I am on the login page
 When I fill in username with "picaWrong"
 And I fill in password with "picaWrong"
 And I press "login"
 Then I should be on the "eScienceCloud - Login" page
 #And I should see "Login failed"