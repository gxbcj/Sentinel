/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.annotation.aspectj.integration;

import com.alibaba.csp.sentinel.annotation.aspectj.integration.config.AopTestConfig;
import com.alibaba.csp.sentinel.annotation.aspectj.integration.service.FooService;
import com.alibaba.csp.sentinel.annotation.aspectj.integration.service.FooUtil;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.util.MethodUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for Sentinel annotation AspectJ extension.
 *
 * @author Eric Zhao
 */
@ContextConfiguration(classes = {SentinelAnnotationIntegrationTest.class, AopTestConfig.class})
public class SentinelAnnotationIntegrationTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    private FooService fooService;

    @Test
    public void testProxySuccessful() {
        assertThat(AopUtils.isAopProxy(fooService)).isTrue();
        assertThat(AopUtils.isCglibProxy(fooService)).isTrue();
    }

    @Test
    public void testForeignBlockHandlerClass() throws Exception {
        assertThat(fooService.random()).isNotEqualTo(FooUtil.BLOCK_FLAG);
        String resourceName = MethodUtil.resolveMethodName(FooService.class.getDeclaredMethod("random"));
        ClusterNode cn = ClusterBuilderSlot.getClusterNode(resourceName);
        assertThat(cn).isNotNull();
        assertThat(cn.passQps()).isPositive();

        FlowRuleManager.loadRules(Collections.singletonList(
                new FlowRule(resourceName).setCount(0)
        ));
        assertThat(fooService.random()).isEqualTo(FooUtil.BLOCK_FLAG);
        assertThat(cn.blockQps()).isPositive();
    }

    @Test(expected = UndeclaredThrowableException.class)
    public void testBlockHandlerNotFound() {
        assertThat(fooService.baz("Sentinel")).isEqualTo("cheers, Sentinel");
        String resourceName = "apiBaz";
        ClusterNode cn = ClusterBuilderSlot.getClusterNode(resourceName);
        assertThat(cn).isNotNull();
        assertThat(cn.passQps()).isPositive();

        FlowRuleManager.loadRules(Collections.singletonList(
                new FlowRule(resourceName).setCount(0)
        ));
        fooService.baz("Sentinel");
    }

    @Test
    public void testNormalBlockHandlerAndFallback() throws Exception {
        assertThat(fooService.foo(1)).isEqualTo("Hello for 1");
        String resourceName = "apiFoo";
        ClusterNode cn = ClusterBuilderSlot.getClusterNode(resourceName);
        assertThat(cn).isNotNull();
        assertThat(cn.passQps()).isPositive();

        // Test for fallback.
        assertThat(fooService.foo(9527)).isEqualTo("eee...");

        // Test for biz exception.
        try {
            fooService.foo(5758);
            fail("should not reach here");
        } catch (Exception ex) {
            // Should not be traced.
            assertThat(cn.exceptionQps()).isZero();
        }

        try {
            fooService.foo(5763);
            fail("should not reach here");
        } catch (Exception ex) {
            assertThat(cn.exceptionQps()).isPositive();
        }

        // Test for blockHandler
        FlowRuleManager.loadRules(Collections.singletonList(
                new FlowRule(resourceName).setCount(0)
        ));
        assertThat(fooService.foo(1121)).isEqualTo("Oops, 1121");
        assertThat(cn.blockQps()).isPositive();
    }

    @Before
    public void setUp() throws Exception {
        FlowRuleManager.loadRules(new ArrayList<FlowRule>());
        ClusterBuilderSlot.resetClusterNodes();
    }

    @After
    public void tearDown() throws Exception {
        FlowRuleManager.loadRules(new ArrayList<FlowRule>());
        ClusterBuilderSlot.resetClusterNodes();
    }
}
