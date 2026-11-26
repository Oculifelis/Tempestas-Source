package dev.tempestas.autotest;

import dev.tempestas.TempestasRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Only here to reach the JEI runtime: checks the info pages and the condensing category, then opens it. */
@JeiPlugin
public class AutotestJeiPlugin implements IModPlugin {
    static volatile IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("tempestas_autotest", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    static void check() {
        Set<Item> withInfo = new HashSet<>();
        runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).get()
                .forEach(r -> r.getIngredients().forEach(i -> i.getItemStack().ifPresent(st -> withInfo.add(st.getItem()))));
        Object[][] wanted = {
                {"sky_jar", TempestasRegistry.SKY_JAR.get()}, {"caught_sky", TempestasRegistry.CAUGHT_SKY.get()},
                {"almanac", TempestasRegistry.ALMANAC.get()}, {"climate_gauge", TempestasRegistry.CLIMATE_GAUGE.get()},
                {"ember_charm", TempestasRegistry.EMBER_CHARM.get()}, {"storm_charm", TempestasRegistry.STORM_CHARM.get()},
                {"storm_glass", TempestasRegistry.STORM_GLASS.get()},
        };
        for (Object[] w : wanted) {
            Autotest.check("jei.info." + w[0], withInfo.contains(w[1]), "info page");
        }
        List<IRecipeCategory<?>> ours = runtime.getRecipeManager().createRecipeCategoryLookup().get()
                .filter(c -> c.getRecipeType().getUid().getNamespace().equals("tempestas")).<IRecipeCategory<?>>map(c -> c).toList();
        Autotest.check("jei.category", !ours.isEmpty(), "tempestas categories: "
                + ours.stream().map(c -> c.getRecipeType().getUid().toString()).toList());
        if (!ours.isEmpty()) {
            long n = runtime.getRecipeManager().createRecipeLookup(ours.getFirst().getRecipeType()).get().count();
            Autotest.check("jei.recipes", n > 0, n + " recipes in " + ours.getFirst().getRecipeType().getUid());
            runtime.getRecipesGui().showTypes(List.of(ours.getFirst().getRecipeType()));
        }
    }
}
