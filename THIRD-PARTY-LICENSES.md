# Third-Party Licenses

This document lists all third-party dependencies used in AI Arena and their licenses.

## License Summary

All dependencies use permissive open-source licenses (Apache-2.0 or MIT).

| Dependency | License | Type |
|------------|---------|------|
| RESTEasy | Apache-2.0 | Permissive |
| Jakarta Servlet API | EPL-2.0 | Permissive |
| Jackson | Apache-2.0 | Permissive |
| Auth0 java-jwt | MIT | Permissive |
| Auth0 jwks-rsa | MIT | Permissive |
| SLF4J | MIT | Permissive |
| Log4j2 | Apache-2.0 | Permissive |
| JUnit 5 | EPL-2.0 | Permissive (Test) |
| Mockito | MIT | Permissive (Test) |
| Undertow | Apache-2.0 | Permissive (Test) |

## License Compliance

**All production dependencies use Apache-2.0 or MIT licenses** which are:
- Permissive open-source licenses
- Allow commercial use, modification, and distribution
- Do not require derivative works to use the same license
- Compatible with proprietary software

No GPL, LGPL, or other copyleft licenses are used in production code.

## Dependency Details

### RESTEasy (JAX-RS Implementation)
- **License:** Apache License 2.0
- **URL:** https://resteasy.dev/
- **Components:** resteasy-servlet-initializer, resteasy-jackson2-provider

### Log4j2 (Logging)
- **License:** Apache License 2.0
- **URL:** https://logging.apache.org/log4j/2.x/
- **Components:** log4j-api, log4j-core, log4j-slf4j2-impl

### Jackson (JSON Processing)
- **License:** Apache License 2.0
- **URL:** https://github.com/FasterXML/jackson
- **Components:** jackson-core, jackson-databind, jackson-annotations

### Auth0 Libraries (JWT)
- **License:** MIT
- **URL:** https://github.com/auth0/java-jwt
- **Components:** java-jwt, jwks-rsa

### SLF4J (Logging API)
- **License:** MIT
- **URL:** https://www.slf4j.org/
- **Components:** slf4j-api

## License Texts

### Apache License 2.0

Used by: RESTEasy, Log4j2, Jackson

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

### MIT License

Used by: Auth0 libraries, SLF4J, Mockito

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Generating License Report

To generate an updated license report:

```bash
mvn license:add-third-party
```

This will create a `THIRD-PARTY.txt` file with all dependency licenses.
