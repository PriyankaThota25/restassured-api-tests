# Rest Assured API tests

Java 21 + Maven + Rest Assured 5.5.0 + JUnit 5.

## Prerequisites
- JDK 17+ (`java -version`)
- Maven (`brew install maven`)

## Run
```
mvn test
mvn test -Dbase.uri=https://other-env.example.com   # override target
mvn test -Dtest=PostsApiTest#getPost_returnsExpectedFields
```
Reports: `target/surefire-reports/`

## Layout
- `src/test/resources/config.properties` – base URI
- `BaseApiTest` – shared request spec (base URI, JSON headers)
- `PostsApiTest` – sample tests against jsonplaceholder.typicode.com
