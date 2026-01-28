# Third-Party Licenses

This document lists all third-party dependencies used in AI Arena and their licenses.

## License Summary

| Dependency | License | Type |
|------------|---------|------|
| Jakarta Servlet API | EPL-2.0 | Permissive |
| Jersey | EPL-2.0 | Permissive |
| HK2 | EPL-2.0 | Permissive |
| Jackson | Apache-2.0 | Permissive |
| Auth0 java-jwt | MIT | Permissive |
| Auth0 jwks-rsa | MIT | Permissive |
| SLF4J | MIT | Permissive |
| Logback | EPL-1.0 | Permissive |
| JUnit 5 | EPL-2.0 | Permissive (Test) |
| Mockito | MIT | Permissive (Test) |

## Dual-Licensed Dependencies

The following dependencies are dual-licensed. We choose the permissive license variant:

### Jakarta EE / Eclipse EE4J

**Components:** Jakarta Servlet API, Jersey, HK2, Grizzly
**Available Licenses:** EPL-2.0, GPL-2.0 with Classpath Exception
**License Used:** EPL-2.0 (Eclipse Public License 2.0)

The Eclipse Public License 2.0 is a permissive open-source license that allows:
- Commercial use
- Modification
- Distribution
- Private use

### Logback

**Available Licenses:** EPL-1.0, LGPL-2.1
**License Used:** EPL-1.0 (Eclipse Public License 1.0)

## License Texts

### Apache License 2.0

Used by: Jackson, Maven plugins

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

### Eclipse Public License 2.0 (EPL-2.0)

Used by: Jakarta EE, Jersey, HK2, JUnit 5, Logback

```
Eclipse Public License - v 2.0

THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE
PUBLIC LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION
OF THE PROGRAM CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.

Full text: https://www.eclipse.org/legal/epl-2.0/
```

## Compliance Notes

1. **All dependencies use permissive licenses** that allow commercial use, modification, and redistribution.

2. **No copyleft/viral licenses** (GPL without exceptions) are used in production code.

3. **Test-scope dependencies** (JUnit, Mockito) are not distributed with the final product.

4. **Dual-licensed packages** explicitly use the permissive license option (EPL) instead of GPL.

## Generating License Report

To generate an updated license report:

```bash
mvn license:add-third-party
```

This will create a `THIRD-PARTY.txt` file with all dependency licenses.
