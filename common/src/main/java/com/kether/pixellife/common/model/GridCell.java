package com.kether.pixellife.common.model;

/**
 * Représente une cellule discrète dans la grille.
 * Utilisé uniquement comme clé pour l'index spatial.
 */
public record GridCell(int x, int y) {}