default: build

build:
	mvn clean install -DskipTests -Dcargo-build.profile=release

fmt:
	mvn formatter:format

pkg:
	mvn clean package

deploy:
	mvn clean deploy

test:
	mvn clean test
