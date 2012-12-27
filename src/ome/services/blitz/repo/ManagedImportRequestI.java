/*
 * Copyright (C) 2012 Glencoe Software, Inc. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package ome.services.blitz.repo;

import java.util.List;
import java.util.Map;

import Ice.Current;

import ome.system.OmeroContext;

import omero.cmd.ERR;
import omero.cmd.Helper;
import omero.cmd.IRequest;
import omero.cmd.OK;
import omero.cmd.Response;
import omero.grid.ImportRequest;
import omero.grid.ImportResponse;
import omero.model.Pixels;

/**
 * Wrapper around {@link FilesetJobLink} instances which need to be handled
 * on the server-side. This will primarily provide the step-location required
 * by {@link omero.cmd.Handle} by calling back to the
 * {@link ManagedImportProcessI} object.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 4.5.0
 */
public class ManagedImportRequestI extends ImportRequest implements IRequest {

    private static final long serialVersionUID = -303948503984L;

    /**
     * Helper instance for this class. Will create a number of sub-helper
     * instances for each request.
     */
    private Helper helper;

    private final ManagedImportProcessI proc;

    public ManagedImportRequestI(ManagedImportProcessI proc) {
        this.proc = proc;
    }

    //
    // IRequest methods
    //

    public Map<String, String> getCallContext() {
        return null;
    }

    public void init(Helper helper) {
        this.helper = helper;
        helper.setSteps(1);
    }

    public Object step(int step) {
        helper.assertStep(step);
        try {
            return proc.importMetadata();
        } catch (Throwable t) {
            throw helper.cancel(new ERR(), t, "import-metadata-failed");
        }
    }

    public void buildResponse(int step, Object object) {
        helper.assertResponse(step);
        helper.setResponseIfNull(new ImportResponse((List<Pixels>) object));
    }

    public Response getResponse() {
        return helper.getResponse();
    }

}
