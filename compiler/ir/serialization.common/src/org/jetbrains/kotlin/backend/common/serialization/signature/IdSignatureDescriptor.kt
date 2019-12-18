/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.descriptors.WrappedDeclarationDescriptor
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.IdSignatureComposer
import org.jetbrains.kotlin.ir.util.KotlinMangler

open class IdSignatureDescriptor(private val mangler: KotlinMangler.DescriptorMangler) : IdSignatureComposer {

    protected open fun createSignatureBuilder(): DescriptorBasedSignatureBuilder = DescriptorBasedSignatureBuilder(mangler)

    protected open class DescriptorBasedSignatureBuilder(private val mangler: KotlinMangler.DescriptorMangler) :
        IdSignatureBuilder<DeclarationDescriptor>(),
        DeclarationDescriptorVisitor<Unit, Nothing?> {

        override fun accept(d: DeclarationDescriptor) {
            d.accept(this, null)
        }

        private fun reportUnexpectedDescriptor(descriptor: DeclarationDescriptor) {
            error("unexpected descriptor $descriptor")
        }

        private fun collectFqNames(descriptor: DeclarationDescriptorNonRoot) {
            descriptor.containingDeclaration.accept(this, null)
            classFanSegments.add(descriptor.name.asString())
        }

        override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, data: Nothing?) {
            packageFqn = descriptor.fqName
        }

        override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, data: Nothing?) {
            packageFqn = descriptor.fqName
        }

        override fun visitVariableDescriptor(descriptor: VariableDescriptor, data: Nothing?) = reportUnexpectedDescriptor(descriptor)

        override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: Nothing?) {
            hash_id = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
            platformSpecificFunction(descriptor)
        }

        override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(descriptor)

        override fun visitClassDescriptor(descriptor: ClassDescriptor, data: Nothing?) {
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
            platformSpecificClass(descriptor)
        }

        override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: Nothing?) {
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
            platformSpecificAlias(descriptor)
        }

        override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: Nothing?) = reportUnexpectedDescriptor(descriptor)

        override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor, data: Nothing?) {
            hash_id = mangler.run { mangleDeclaration(constructorDescriptor).hashMangle() }
            collectFqNames(constructorDescriptor)
            platformSpecificConstructor(constructorDescriptor)
        }

        override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(scriptDescriptor)

        override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: Nothing?) {
            hash_id = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
            platformSpecificProperty(descriptor)
        }

        override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(descriptor)

        override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor, data: Nothing?) {
            hash_id_acc = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            descriptor.correspondingProperty.accept(this, null)
            classFanSegments.add(descriptor.name.asString())
            setExpected(descriptor.isExpect)
            platformSpecificGetter(descriptor)
        }

        override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor, data: Nothing?) {
            hash_id_acc = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            descriptor.correspondingProperty.accept(this, null)
            classFanSegments.add(descriptor.name.asString())
            setExpected(descriptor.isExpect)
            platformSpecificSetter(descriptor)
        }

        override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(descriptor)
    }

    private val composer by lazy { createSignatureBuilder() }

    override fun composeSignature(descriptor: DeclarationDescriptor): IdSignature? {
        if (descriptor is WrappedDeclarationDescriptor<*>) return null
        return if (mangler.isExport(descriptor)) {
            composer.buildSignature(descriptor)
        } else null
    }

    override fun composeEnumEntrySignature(descriptor: ClassDescriptor): IdSignature? {
        return if (mangler.isExportEnumEntry(descriptor)) {
            composer.buildSignature(descriptor)
        } else null
    }
}