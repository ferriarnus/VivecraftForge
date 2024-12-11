package org.vivecraft.client.extensions;

public interface RenderTargetExtension {

    /**
     * sets the OpenGL texture id to use, if set, it will be used, instead of generating a new one
     * @param texId texture id to use
     */
    void vivecraft$setTexId(int texId);

    /**
     * sets if a combined depth/stencil should be used
     * @param stencil if a stencil should be added
     */
    void vivecraft$setStencil(boolean stencil);

    /**
     * @return if the RenderTarget has a stencil added by vivecraft active
     */
    boolean vivecraft$hasStencil();

    /**
     * sets if linear filtering should be used, if false or unset will use nearest filtering.
     * @param linearFilter if linear filtering should be used
     */
    void vivecraft$setLinearFilter(boolean linearFilter);

    /**
     * sets if mipmaps should be used for sampling
     * @param mipmaps if mipmaps should be used
     */
    void vivecraft$setMipmaps(boolean mipmaps);

    /**
     * @return if the RenderTarget is set to use mipmaps
     */
    boolean vivecraft$hasMipmaps();
}
