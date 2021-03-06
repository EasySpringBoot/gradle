/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.logging.console.jvm

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

import static org.gradle.internal.logging.console.jvm.TestedProjectFixture.*

class ConsoleTestNGUnsupportedTestWorkerFunctionalTest extends AbstractConsoleFunctionalSpec {

    private static final int MAX_WORKERS = 2
    private static final String SERVER_RESOURCE_1 = 'test-1'
    private static final String SERVER_RESOURCE_2 = 'test-2'
    private static final String TESTNG_DEPENDENCY = 'org.testng:testng:6.3.1'
    private static final String TESTNG_ANNOTATION = 'org.testng.annotations.Test'
    private static final JavaTestClass TEST_CLASS_1 = JavaTestClass.PRESERVED_TEST1
    private static final JavaTestClass TEST_CLASS_2 = JavaTestClass.PRESERVED_TEST2

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        executer.withArguments('--parallel', "--max-workers=$MAX_WORKERS")
        server.start()
    }

    def "omits parallel test execution if TestNG version does not emit class listener events"() {
        given:
        buildFile << testableJavaProject(TESTNG_DEPENDENCY, MAX_WORKERS)
        buildFile << useTestNG()
        file("src/test/java/${TEST_CLASS_1.fileRepresentation}") << testClass(TESTNG_ANNOTATION, TEST_CLASS_1.classNameWithoutPackage, SERVER_RESOURCE_1, server)
        file("src/test/java/${TEST_CLASS_2.fileRepresentation}") << testClass(TESTNG_ANNOTATION, TEST_CLASS_2.classNameWithoutPackage, SERVER_RESOURCE_2, server)
        def testExecution = server.expectConcurrentAndBlock(2, SERVER_RESOURCE_1, SERVER_RESOURCE_2)

        when:
        def gradleHandle = executer.withTasks('test').start()
        testExecution.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert !containsTestExecutionWorkInProgressLine(gradleHandle, ':test', TEST_CLASS_1.renderedClassName)
            assert !containsTestExecutionWorkInProgressLine(gradleHandle, ':test', TEST_CLASS_2.renderedClassName)
        }

        testExecution.release(2)
        gradleHandle.waitForFinish()
    }
}
