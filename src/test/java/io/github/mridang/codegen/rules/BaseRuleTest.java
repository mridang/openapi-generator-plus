package io.github.mridang.codegen.rules;

import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;

/**
 * An abstract base class for rule tests to reduce boilerplate.
 * It automatically instantiates the rule-under-test.
 *
 * @param <T> The type of the CustomNormalizationRule being tested.
 */
public abstract class BaseRuleTest<T extends CustomNormalizationRule> {

    protected static final Logger logger = LoggerFactory.getLogger(BaseRuleTest.class);
    protected T rule;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        Class<T> ruleClass = (Class<T>) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[0];
        this.rule = ruleClass.getDeclaredConstructor().newInstance();
    }
}
