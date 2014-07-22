/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.model.collection.NamedItemCollectionBuilder
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.registry.DefaultInputs
import org.gradle.model.internal.registry.DefaultModelRegistry
import spock.lang.Specification

class PolymorphicDomainObjectContainerModelAdapterTest extends Specification {

    static class NamedThing implements Named {
        final String name
        String other

        NamedThing(String name) {
            this.name = name
        }
    }

    static class SpecialNamedThing extends NamedThing {
        SpecialNamedThing(String name) {
            super(name)
        }
    }

    static class ThingContainer extends DefaultPolymorphicDomainObjectContainer<NamedThing> {
        ThingContainer(Class<NamedThing> type, Instantiator instantiator) {
            super(type, instantiator)
        }
    }

    def container = new ThingContainer(NamedThing, new DirectInstantiator()) {
        @Override
        protected <U extends NamedThing> U doCreate(String name, Class<U> type) {
            type.newInstance(name)
        }

        {
            registerDefaultFactory {
                new NamedThing(it)
            }
        }
    }

    def adapter = new PolymorphicDomainObjectContainerModelAdapter<NamedThing, ThingContainer>(
            container, ModelType.of(ThingContainer), ModelType.of(NamedThing)
    )

    def builderType = new ModelType<NamedItemCollectionBuilder<NamedThing>>() {}

    def registry = new DefaultModelRegistry()
    def reference = ModelReference.of("container", ThingContainer)
    def creator = new ModelCreator() {
        @Override
        ModelPath getPath() {
            return reference.path
        }

        @Override
        ModelPromise getPromise() {
            adapter.asPromise()
        }

        @Override
        ModelAdapter create(Inputs inputs) {
            adapter
        }

        @Override
        List<? extends ModelReference<?>> getInputs() {
            []
        }

        @Override
        ModelRuleDescriptor getDescriptor() {
            new SimpleModelRuleDescriptor("container")
        }
    }

    def setup() {
        registry.create(creator)
    }

    def "can view as read types"() {
        expect:
        adapter.asReadOnly(ModelType.of(ThingContainer)).instance.is(container)
        adapter.asReadOnly(builderType) == null // only a writable type
    }

    def "can view as write types"() {
        expect:
        adapter.asWritable(reference, new SimpleModelRuleDescriptor("write"), new DefaultInputs([]), registry).instance.is(container)
        adapter.asWritable(ModelReference.of(reference.path, builderType), new SimpleModelRuleDescriptor("write"), new DefaultInputs([]), registry).instance instanceof NamedItemCollectionBuilder
    }

    def "can create items"() {
        // don't need to test too much here, assume that DefaultNamedItemCollectionBuilder is used internally
        given:
        def builder = adapter.asWritable(ModelReference.of(reference.path, builderType), new SimpleModelRuleDescriptor("write"), new DefaultInputs([]), registry).instance

        when:
        builder.create("foo") {
            it.other = "foo-changed"
        }

        builder.create("bar", SpecialNamedThing) {
            it.other = "foo-changed"
        }

        then:
        registry.get(ModelReference.of(reference.path.child("foo"), NamedThing)).name == "foo"

        def bar = registry.get(ModelReference.of(reference.path.child("bar"), NamedThing))
        bar.name == "bar"
        bar instanceof SpecialNamedThing
    }
}
