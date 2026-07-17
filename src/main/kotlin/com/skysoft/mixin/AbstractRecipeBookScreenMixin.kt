package com.skysoft.mixin

import com.skysoft.features.misc.VanillaRecipeBookHider
import com.skysoft.utils.SkysoftErrorBoundary
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AbstractRecipeBookScreen::class)
abstract class AbstractRecipeBookScreenMixin {
    @field:Shadow
    @field:Final
    private lateinit var recipeBookComponent: RecipeBookComponent<*>

    @Inject(
        method = ["init"],
        at = [
            At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;updateScreenPosition(II)I",
            ),
        ],
    )
    protected fun skysoftHideRecipeBookBeforePositioning(ci: CallbackInfo) {
        if (SkysoftErrorBoundary.value("Vanilla Recipe Book positioning", false, ::shouldSkysoftHideInventoryRecipeBook)) {
            (recipeBookComponent as RecipeBookComponentAccessor).skysoftSetVisible(false)
        }
    }

    @Inject(method = ["initButton"], at = [At("HEAD")], cancellable = true)
    protected fun skysoftSkipRecipeBookButton(ci: CallbackInfo) {
        if (SkysoftErrorBoundary.value("Vanilla Recipe Book button", false, ::shouldSkysoftHideInventoryRecipeBook)) {
            ci.cancel()
        }
    }

    @Redirect(
        method = ["extractSlots"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;" +
                "extractGhostRecipe(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V",
        ),
    )
    protected fun skysoftSuppressRecipeBookGhostRecipe(
        component: RecipeBookComponent<*>,
        context: GuiGraphicsExtractor,
        biggerResultSlot: Boolean,
    ) {
        val shouldHide = SkysoftErrorBoundary.value("Vanilla Recipe Book ghost recipe", false) {
            shouldSkysoftHideInventoryRecipeBook()
        }
        if (!shouldHide) component.extractGhostRecipe(context, biggerResultSlot)
    }

    @Unique
    private fun shouldSkysoftHideInventoryRecipeBook(): Boolean =
        (this as Any) is InventoryScreen && VanillaRecipeBookHider.shouldHideInInventory()
}
