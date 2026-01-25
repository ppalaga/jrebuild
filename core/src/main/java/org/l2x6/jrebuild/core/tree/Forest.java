package org.l2x6.jrebuild.core.tree;

import java.util.Collection;

public record Forest<T extends Node<T>>(Collection<T> trees) {

    public Forest<T> mergeOpverlaps() {
        

    }
}
