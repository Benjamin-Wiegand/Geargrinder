package io.benwiegand.projection.geargrinder.coordinate;

import android.view.MotionEvent;

import java.util.function.Function;

import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

public class CoordinateTranslator<T> {

    private final Function<T, Integer> getX;
    private final Function<T, Integer> getY;
    private final Function<Integer, Integer> translateX;
    private final Function<Integer, Integer> translateY;

    private CoordinateTranslator(Function<T, Integer> getX, Function<T, Integer> getY, Function<Integer, Integer> translateX, Function<Integer, Integer> translateY) {
        this.getX = getX;
        this.getY = getY;
        this.translateX = translateX;
        this.translateY = translateY;
    }

    private int getX(T holder) {
        return getX.apply(holder);
    }

    private int getY(T holder) {
        return getY.apply(holder);
    }

    public int translateX(T holder) {
        return translateX.apply(getX(holder));
    }

    public int translateY(T holder) {
        return translateY.apply(getY(holder));
    }

    public CoordinateTranslator<T> chain(CoordinateTranslator<T> next) {
        return new CoordinateTranslator<>(
                getX, getY,
                x -> next.translateX.apply(translateX.apply(x)),
                y -> next.translateY.apply(translateY.apply(y))
        );
    }

    public static CoordinateTranslator<TouchEvent.PointerLocation> createTouchEvent(Function<Integer, Integer> translateX, Function<Integer, Integer> translateY) {
        return new CoordinateTranslator<>(TouchEvent.PointerLocation::x, TouchEvent.PointerLocation::y, translateX, translateY);
    }

    public static CoordinateTranslator<MotionEvent.PointerCoords> createMotionEvent(Function<Integer, Integer> translateX, Function<Integer, Integer> translateY) {
        return new CoordinateTranslator<>(pc -> (int) pc.x, pc -> (int) pc.y, translateX, translateY);
    }

    public static CoordinateTranslator<TouchEvent.PointerLocation> createTouchEventPassthrough() {
        return createTouchEvent(x -> x, y -> y);
    }

}
