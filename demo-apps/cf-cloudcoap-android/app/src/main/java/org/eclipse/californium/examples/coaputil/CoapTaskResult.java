/*******************************************************************************
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - Initial creation
 ******************************************************************************/
package org.eclipse.californium.examples.coaputil;

import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;

import java.util.Set;

public class CoapTaskResult extends CoapProgress {
    public final String rid;
    public final CoapResponse response;
    public final Set<WebLink> links;
    public final Throwable error;

    public CoapTaskResult(CoapProgress progress, CoapResponse response, String rid) {
        super(progress);
        this.rid = rid;
        this.response = response;
        this.links = null;
        this.error = null;
    }

    public CoapTaskResult(CoapProgress progress, CoapResponse response, Set<WebLink> links, String rid) {
        super(progress);
        this.rid = rid;
        this.response = response;
        this.links = links;
        this.error = null;
    }

    public CoapTaskResult(CoapProgress progress, Throwable error, String rid) {
        super(progress);
        this.rid = rid;
        this.error = error;
        this.response = null;
        this.links = null;
    }
}
