package io.wispforest.owo.ui.core;

import io.wispforest.owo.ui.parsing.IncompatibleUIModelException;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.ScissorStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public interface ParentComponent extends Component {

    /**
     * Recalculate the layout of this component
     */
    void layout(Size space);

    /**
     * Called when a child of this parent component has been mutated in some way
     * that would affect the layout of this component
     *
     * @param child The child that has been mutated
     */
    void onChildMutated(Component child);

    /**
     * Queue a task to be run after the
     * entire UI has finished updating
     *
     * @param task The task to run
     */
    void queue(Runnable task);

    /**
     * Set how this component should arrange its children
     *
     * @param horizontalAlignment The horizontal alignment method to use
     * @param verticalAlignment   The vertical alignment method to use
     */
    default ParentComponent alignment(HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        this.horizontalAlignment(horizontalAlignment);
        this.verticalAlignment(verticalAlignment);
        return this;
    }

    /**
     * Set how this component should vertically arrange its children
     *
     * @param alignment The new alignment method to use
     */
    ParentComponent verticalAlignment(VerticalAlignment alignment);

    /**
     * @return How this component vertically arranges its children
     */
    VerticalAlignment verticalAlignment();

    /**
     * Set how this component should horizontally arrange its children
     *
     * @param alignment The new alignment method to use
     */
    ParentComponent horizontalAlignment(HorizontalAlignment alignment);

    /**
     * @return How this component horizontally arranges its children
     */
    HorizontalAlignment horizontalAlignment();

    /**
     * Set the internal padding of this component
     *
     * @param padding The new padding to use
     */
    ParentComponent padding(Insets padding);

    /**
     * @return The internal padding of this component
     */
    AnimatableProperty<Insets> padding();

    /**
     * Set if this component should let its children overflow
     * its bounding box
     *
     * @param allowOverflow {@code true} if this component should let
     *                      its children overflow its bounding box
     */
    ParentComponent allowOverflow(boolean allowOverflow);

    /**
     * @return {@code true} if this component allows its
     * children to overflow its bounding box
     */
    boolean allowOverflow();

    /**
     * Set the surface this component uses
     *
     * @param surface The new surface to use
     */
    ParentComponent surface(Surface surface);

    /**
     * @return The surface this component currently uses
     */
    Surface surface();

    /**
     * @return The children of this component
     */
    List<Component> children();

    /**
     * Remove the given child from this component
     */
    ParentComponent removeChild(Component child);

    @Override
    default void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        Component.super.drawTooltip(context, mouseX, mouseY, partialTicks, delta);

        if (!this.allowOverflow()) {
            var padding = this.padding().get();
            ScissorStack.push(this.x() + padding.left(), this.y() + padding.top(), this.width() - padding.horizontal(), this.height() - padding.vertical(), context.getMatrices());
        }

        for (var child : this.children()) {
            if (!ScissorStack.isVisible(mouseX, mouseY, context.getMatrices())) continue;

            context.getMatrices().translate(0, 0, child.zIndex());
            child.drawTooltip(context, mouseX, mouseY, partialTicks, delta);
            context.getMatrices().translate(0, 0, -child.zIndex());
        }

        if (!this.allowOverflow()) {
            ScissorStack.pop();
        }
    }

    @Override
    default boolean onMouseDown(double mouseX, double mouseY, int button) {
        var iter = this.children().listIterator(this.children().size());

        while (iter.hasPrevious()) {
            var child = iter.previous();
            if (!child.isInBoundingBox(this.x() + mouseX, this.y() + mouseY)) continue;
            if (child.onMouseDown(this.x() + mouseX - child.x(), this.y() + mouseY - child.y(), button)) {
                return true;
            }
        }

        return false;
    }

    @Override
    default boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        var iter = this.children().listIterator(this.children().size());

        while (iter.hasPrevious()) {
            var child = iter.previous();
            if (!child.isInBoundingBox(this.x() + mouseX, this.y() + mouseY)) continue;
            if (child.onMouseScroll(this.x() + mouseX - child.x(), this.y() + mouseY - child.y(), amount)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @apiNote When overriding update and calling {@code ParentComponent.super.update()},
     * ensure that {@link Component#update(float, int, int)} is called as well, through some means
     */
    @Override
    default void update(float delta, int mouseX, int mouseY) {
        this.padding().update(delta);

        for (int i = 0; i < this.children().size(); i++) {
            this.children().get(i).update(delta, mouseX, mouseY);
        }
    }

    @Override
    default void parseProperties(UIModel model, Element element, Map<String, Element> children) {
        Component.super.parseProperties(model, element, children);
        UIParsing.apply(children, "padding", Insets::parse, this::padding);
        UIParsing.apply(children, "surface", Surface::parse, this::surface);
        UIParsing.apply(children, "vertical-alignment", VerticalAlignment::parse, this::verticalAlignment);
        UIParsing.apply(children, "horizontal-alignment", HorizontalAlignment::parse, this::horizontalAlignment);
        UIParsing.apply(children, "allow-overflow", UIParsing::parseBool, this::allowOverflow);
    }

    /**
     * Recursively find the child with the given id in the
     * hierarchy below this component
     *
     * @param id The id to search for
     * @return The child with the given id, or {@code null} if
     * none was found
     */
    @SuppressWarnings("unchecked")
    default <T extends Component> @Nullable T childById(@NotNull Class<T> expectedClass, @NotNull String id) {
        var iter = this.children().listIterator(this.children().size());

        while (iter.hasPrevious()) {
            var child = iter.previous();
            if (Objects.equals(child.id(), id)) {

                if (!expectedClass.isAssignableFrom(child.getClass())) {
                    throw new IncompatibleUIModelException(
                            "Expected child with id '" + id + "'"
                                    + " to be a " + expectedClass.getSimpleName()
                                    + " but it is a " + child.getClass().getSimpleName()
                    );
                }

                return (T) child;
            } else if (child instanceof ParentComponent parent) {
                var candidate = parent.childById(expectedClass, id);
                if (candidate != null) return candidate;
            }
        }

        return null;
    }

    /**
     * Get the most specific child at the given coordinates
     *
     * @param x The x-coordinate to query
     * @param y The y-coordinate to query
     * @return The most specific child at the given coordinates,
     * or {@code null} if there is none
     */
    default @Nullable Component childAt(int x, int y) {
        var iter = this.children().listIterator(this.children().size());

        while (iter.hasPrevious()) {
            var child = iter.previous();
            if (child.isInBoundingBox(x, y)) {
                if (child instanceof ParentComponent parent) {
                    return parent.childAt(x, y);
                } else {
                    return child;
                }
            }
        }

        return this.isInBoundingBox(x, y) ? this : null;
    }

    /**
     * Collect the entire component hierarchy below the given component
     * into the given list
     *
     * @param into The list into which to collect the hierarchy
     * @deprecated Use {@link #collectDescendants(ArrayList)} instead, it has
     * a much clearer name
     */
    @Deprecated(forRemoval = true)
    default void collectChildren(ArrayList<Component> into) {
        this.forEachDescendant(into::add);
    }

    /**
     * Collect the entire component hierarchy below the given component
     * into the given list
     *
     * @param into The list into which to collect the hierarchy
     */
    default void collectDescendants(ArrayList<Component> into) {
        this.forEachDescendant(into::add);
    }

    /**
     * Run the given callback function for every
     * descendant of this component
     *
     * @param action The action to execute for each descendant
     */
    default void forEachDescendant(Consumer<Component> action) {
        action.accept(this);
        for (var child : this.children()) {
            if (child instanceof ParentComponent parent) {
                parent.forEachDescendant(action);
            } else {
                action.accept(child);
            }
        }
    }
}
