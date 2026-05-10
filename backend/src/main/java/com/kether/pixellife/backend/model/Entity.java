package com.kether.pixellife.backend.model;

import com.kether.pixellife.common.model.Position;
import com.kether.pixellife.backend.engine.SimulationContext;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Entité de base du moteur de simulation.
 */
@Getter
@Setter
public abstract sealed class Entity
        permits Organism, Plant, Nutrient {

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(0);

    protected final long id = ID_SEQUENCE.incrementAndGet();
    protected Position position;
    protected float energy;
    protected boolean alive = true;
    protected float floatX, floatY;

    protected Entity(Position position, float energy) {
        this.position = position;
        this.energy = energy;
        this.floatX = position.x() + 0.5f;
        this.floatY = position.y() + 0.5f;
    }

    /**
     * Cycle de vie de l'entité pour un tick de simulation.
     */
    public abstract void update(SimulationContext context);

    /**
     * Type string pour sérialisation / rendu.
     */
    public abstract String typeName();

    public void consumeEnergy(float amount) {
        energy -= amount;
        if (energy <= 0) {
            energy = 0;
            alive = false;
        }
    }

    public boolean isDead() {
        return !alive || energy <= 0;
    }

    public void die() {
        alive = false;
        energy = 0;
    }

    public int x() { return position.x(); }
    public int y() { return position.y(); }
}