
# Plastic Packaging Tax (PPT) Returns

This is the Scala microservice responsible for the transient storage of PPT returns information and PPT Account section,
which is part of the PPT tax regime, as discussed in this [GovUk Guidance](https://www.gov.uk/government/publications/introduction-of-plastic-packaging-tax/plastic-packaging-tax)
 
### How to run the service

These are the steps to the Plastic Packaging Tax Returns and Account service, of which this microservice is part of.

* Start a MongoDB instance

* Start the microservices
 
```
# Start the plastic packaging services and dependencies 
sm --start PLASTIC_PACKAGING_TAX_ALL -r

# confirm all services are running
sm -s 
```

* Visit http://localhost:9949/auth-login-stub/gg-sign-in
* Add an enrolment key `HMRC-PPT-ORG`, an identifier name `EtmpRegistrationNumber` and an existing value, for example `XMPPT0000000001`
* If a PPT Reference does not exist in the DB, we will need to create one by [creating a PPT Subscription](https://github.com/hmrc/plastic-packaging-tax-registration-frontend)
* Enter the redirect url: http://localhost:8505/plastic-packaging-tax/account and press **Submit**.
  
### Sample curl commands

#### EIS view subscription
```
curl -H "Authorization:Bearer <token>>" "http://localhost:8504/subscriptions/XMPPT0000000001"
```
#### EIS export credit balance
```
curl -H "Authorization:Bearer <token>" "http://localhost:8504/export-credits/XMPPT0000000001?fromDate=2021-08-01&toDate=2021-10-31"
```
#### DES obligation data
```
curl -H "Authorization:Bearer <token>" "http://localhost:8504/obligation-data/XXPPTP103844232?fromDate=2021-08-01&toDate=2021-10-31&status=O"
```
#### DES financial data
```
curl -H "Authorization:Bearer <token>" "http://localhost:8504/financial-data/XXPPTP103844232?fromDate=2021-08-01&toDate=2021-10-31&onlyonlyOpenItems=true&includeLocks=false&calculateAccruedInterest=false&customerPaymentInformation=true"
```


### Precheck

Before submitting a commit or pushing code remotely, please run  
```
./precheck.sh
```
This will execute unit and integration tests, check the Scala style and code coverage

### Scalastyle

Project contains `scalafmt` plugin.

Commands for code formatting:

```
sbt scalafmt        # format compile sources
sbt test:scalafmt   # format test sources
sbt sbt:scalafmt    # format .sbt source
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

