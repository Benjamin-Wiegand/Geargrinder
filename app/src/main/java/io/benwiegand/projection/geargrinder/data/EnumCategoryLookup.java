package io.benwiegand.projection.geargrinder.data;

import java.util.ArrayList;
import java.util.List;

/**
 * category list lookup table that uses enum ordinals as indexes in a 2D List for efficiency.
 * @param <E> enum type (must have ordinals in numerical order starting from 0)
 * @param <T> value type
 */
public class EnumCategoryLookup<E extends Enum<E>, T> {

    private final List<List<T>> lookupTable;
    private boolean immutable = false;

    public EnumCategoryLookup(Class<E> enumClass) {
        E[] constants = enumClass.getEnumConstants();
        assert enumClass.isEnum() && constants != null;

        lookupTable = new ArrayList<>(constants.length);

        for (int i = 0; i < constants.length; i++) {
            assert i == constants[i].ordinal();
            lookupTable.add(new ArrayList<>());
        }
    }

    public void makeImmutable() {
        if (immutable) return;

        for (int i = 0; i < lookupTable.size(); i++) {
            List<T> immutable = List.copyOf(lookupTable.get(i));
            lookupTable.set(i, immutable);
        }

        immutable = true;
    }

    public void clear() {
        for (List<T> categoryList : lookupTable)
            categoryList.clear();
    }

    public void clear(E key) {
        get(key).clear();
    }

    public List<T> get(E key) {
        return lookupTable.get(key.ordinal());
    }

    public boolean isEmpty() {
        for (List<T> categoryList : lookupTable) {
            if (!categoryList.isEmpty()) return false;
        }

        return true;
    }

    public boolean add(E key, T value) {
        return get(key).add(value);
    }

    public int size(E key) {
        return get(key).size();
    }

    public int size() {
        int size = 0;

        for (List<T> categoryList : lookupTable)
            size += categoryList.size();

        return size;
    }

    public static <E extends Enum<E>, T> EnumCategoryLookup<E, T> makeEmptyImmutable(Class<E> enumClass) {
        EnumCategoryLookup<E, T> lookup = new EnumCategoryLookup<>(enumClass);
        lookup.makeImmutable();
        return lookup;
    }

}
