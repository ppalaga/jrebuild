package org.l2x6.jrebuild.core.tree;

import java.util.Objects;

public record Tree<T extends Node<T>>(T rootNode) {

    public Tree(T rootNode) {
        this.rootNode = Objects.requireNonNull(rootNode);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(rootNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Tree<T> other = (Tree<T>) obj;
        return rootNode.deepEquals(other.rootNode);
    }


}
