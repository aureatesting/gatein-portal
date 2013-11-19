/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.gatein.portal.web.page;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import juzu.request.Phase;
import org.gatein.portal.mop.customization.CustomizationContext;
import org.gatein.portal.mop.customization.CustomizationService;
import org.gatein.portal.mop.hierarchy.NodeContext;
import org.gatein.portal.mop.hierarchy.NodeModel;
import org.gatein.portal.mop.layout.ElementState;
import org.gatein.portal.content.ContentProvider;
import org.gatein.portal.content.ProviderRegistry;
import org.gatein.portal.content.WindowContent;

/**
 * Encapsulate state and operations on a page.
 *
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 */
public class PageContext implements Iterable<Map.Entry<String, WindowContext>> {

    /**
     * The page builder.
     */
    public static class Builder {

        public Builder(String path) {
            this.state = new PageData(path);
        }

        public Builder(PageData state) {
            this.state = state;
        }

        /** . */
        private PageData state;

        /** A map of name -> window. */
        private final HashMap<String, WindowContent<?>> windows = new LinkedHashMap<String, WindowContent<?>>();

        /** A map of name -> state. */
        private final HashMap<String, Serializable> windowStates = new LinkedHashMap<String, Serializable>();

        public WindowContent<?> getWindow(String name) {
            return windows.get(name);
        }

        public NodeModel<NodeState, ElementState> asModel(
                final ProviderRegistry providerRegistry,
                final CustomizationService customizationService) {
            return new NodeModel<NodeState, ElementState>() {
                @Override
                public NodeContext<NodeState, ElementState> getContext(NodeState node) {
                    return node.context;
                }
                @Override
                public NodeState create(NodeContext<NodeState, ElementState> context) {
                    if (context.getState() instanceof ElementState.Window) {
                        CustomizationContext<?> customization = customizationService.loadCustomization(context.getId());
                        String contentId = customization.getContentId();
                        NodeState window = new NodeState(context);
                        ContentProvider<?> contentProvider = providerRegistry.resolveProvider(customization.getContentType().getValue());
                        WindowContent<?> windowState = contentProvider.getContent(contentId);
                        windows.put(window.context.getName(), windowState);
                        windowStates.put(window.context.getName(), customization.getState());
                        return window;
                    } else {
                        return new NodeState(context);
                    }
                }
            };
        }

        public void setWindow(String name, WindowContent<?> window) {
            windows.put(name, window);
        }

        public void apply(Iterable<Map.Entry<QName, String[]>> changes) {
            state.apply(changes);
        }

        public void setParameters(Map<String, String[]> parameters) {
            HashMap<QName, String[]> prp = new HashMap<QName, String[]>(parameters.size());
            for (Map.Entry<String, String[]> parameter : parameters.entrySet()) {
                prp.put(new QName(parameter.getKey()), parameter.getValue());
            }
            state.setParameters(prp);
        }

        public void setQNameParameters(Map<QName, String[]> parameters) {
            state.setParameters(parameters);
        }

        public PageContext build() {
            return new PageContext(this);
        }
    }

    /** The canonical navigation path. */
    public final PageData state;

    /** A map of name -> window. */
    private final HashMap<String, WindowContext> windowMap;

    /** Windows iteration. */
    public final Iterable<WindowContext> windows;

    public PageContext(Builder builder) {

        //
        LinkedHashMap<String, WindowContext> a = new LinkedHashMap<String, WindowContext>(builder.windows.size());
        for (Map.Entry<String, WindowContent<?>> window : builder.windows.entrySet()) {
            Serializable state = builder.windowStates.get(window.getKey());
            a.put(window.getKey(), new WindowContext(window.getKey(), window.getValue(), state, this));
        }

        //
        this.state = builder.state;
        this.windowMap = a;
        this.windows = a.values();
    }

    public Builder builder() {

        Builder builder = new Builder(new PageData(state));

        // Clone the windows
        for (Map.Entry<String, WindowContext> entry : windowMap.entrySet()) {
            WindowContext window = entry.getValue();
            builder.windows.put(window.name, window.content.copy());
            builder.windowStates.put(window.name, window.state);
        }

        //
        return builder;
    }

    public WindowContext get(String name) {
        return windowMap.get(name);
    }

    @Override
    public Iterator<Map.Entry<String, WindowContext>> iterator() {
        return windowMap.entrySet().iterator();
    }

    public boolean hasParameters() {
        return state.getParameters() != null;
    }

    public Map<QName, String[]> getParameters() {
        return state.getParameters();
    }

    //

    public void encodeParameters(Phase.View.Dispatch dispatch) {
        Map<QName, String[]> parameters = state.getParameters();
        HashMap<String, String[]> a = new HashMap<String, String[]>(parameters.size());
        for (Map.Entry<QName, String[]> b : parameters.entrySet()) {
            a.put(b.getKey().getLocalPart(), b.getValue());
        }
        Encoder encoder = new Encoder(a);
        dispatch.setParameter(WindowContext.ENCODING, "javax.portlet.p", encoder.encode());
    }

    public Phase.View.Dispatch getDispatch() {
        Phase.View.Dispatch view = Controller_.index(state.path, null, null, null, null);
        for (WindowContext w : windows) {
            w.encode(view);
        }
        if (hasParameters()) {
            for (Map.Entry<QName, String[]> parameter : state.getParameters().entrySet()) {
                view.setParameter(parameter.getKey().getLocalPart(), parameter.getValue());
            }
        }
        return view;
    }
}