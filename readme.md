[![Build Stream Services](https://github.com/Backbase/stream-services-2.0/actions/workflows/build.yml/badge.svg)](https://github.com/Backbase/stream-services-2.0/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.backbase.stream%3Astream-services&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.backbase.stream%3Astream-services)
# Stream Services
Stream Services are the "out-of-the-box" components that talk to DBS are responsible for orchestrating calls to DBS. 
The orchestration of calling different services is written in a functional programming style on project reactor enabling resiliency and scalability. 
Stream Services are packaged as libraries that can be included in any project, REST Service and/or Spring Cloud Data Flow Applications

The Services are listed with Service name and how they are packaged

Currently the following DBS services are exposed as Stream Components:
* [Stream DBS Clients](stream-dbs-clients/readme.md) -> The Stream DBS Clients are generated from Backbase OOTB specs and provide a reactive interface to interact with DBS services.
* [Stream-Cursor](stream-cursor/readme.md) (Lib, Rest, Source)  → The Stream Cursor Source is listening to predefined DBS events such as On Login. For each login event, it will retrieve a list of products from entitlements and creates an Ingestion Cursor per product. Cursors can be stored in a RDBMS and published on a HTTP Web Socket or Spring Cloud Dataflow Source. Requires RDBMS, Access Control, Product Summary and Transactions. Login Event received from Authentication Starter or Identity (with Audit Events enabled)
* [Stream Access Control](stream-cursor/readme.md) (Lib) → The Stream Access Control library provides access to Access Control services from a single library and provides an easier abstraction layer to use these services. It mixes access to persistence and service api's to enable proper service to service comms for non DBS services such as Stream. Requires Access Control, Product Summary
* [Stream Legal Entity](stream-cursor/readme.md) (Lib, Rest, Sink) → Legal Entity Ingestion Service that orchestrate all calls to DBS from a single aggregate model. This service is exposed as a library, REST full service and as a Sink. Supports retry of the aggregate and uses the Legal Entity Ingestion Model to have a single interface into DBS. Requires Access Control, Product Summary
* [Stream Product Catalog](stream-cursor/readme.md) (Lib, Rest, Task) → Enabled bootstrapping of product types into DBS. Product Types are currently hardcoded in the streamTask definition. Orchestrates calls into Product Summary
* [Stream Transactions](stream-cursor/readme.md) (Lib, Rest, Sink) → Allows ingestion into DBS in a controlled way with support for retry, rate limiting and configurable batch sizes. 

## Supported DBS versions

| DBS version | Stream [version](https://github.com/Backbase/stream-services-2.0/releases) |
|-------------|-------------------------------------------------------------------------|
| 2021.07 (2.21.x) | 2.44.0 to latest                                                   |
| 2.20.x           | 2.23.0 to 2.43.0                                                   |
| 2.19.x           | 2.1.0 to 2.22.0                                                    |

For previous DBS version please check Stream v1: https://github.com/Backbase/stream-services 

## Contributing
You are welcome to provide bug fixes and new features in the form of pull requests. If you'd like to contribute, please be mindful of the following guidelines:

- All changes should be properly tested for common scenarios (i.e. if changing Legal Entity SAGA, test that your change doesn't affect in non-intended ways to LE ingestion and update).
- Please make one change/feature per pull request.
- Use descriptive PR description and commit messages.
- Together with your changes, submit updated `CHANGELOG.md` in your PR
- Try to avoid reformat of files that change the indentation, tabs to spaces etc., as this makes reviewing diffs much more difficult.
