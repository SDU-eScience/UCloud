Feature: Delete a permissionset

  Scenario: Delete permissionset

    Given The transaction holds a valid jwt

    And The transaction holds a valid inbound payload

    And a connection can be obtained to the database

    When When the user press save the gateway passes the command to the db

    Then a message should be returned to confirm that the transaction was successful