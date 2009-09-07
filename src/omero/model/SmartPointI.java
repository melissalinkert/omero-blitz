/*
 *   $Id$
 *
 *   Copyright 2009 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 *
 */
package omero.model;

import static omero.rtypes.rdouble;

import java.awt.Shape;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SmartPointI extends omero.model.PointI implements SmartShape {

    public void areaPoints(PointCallback cb) {
        cb.handle((int) cx.getValue(), (int) cy.getValue());
    }

    public Shape asAwtShape() {
        String path = Util.pointsToPath(asPoints(), true);
        return Util.parseAwtPath(path);
    }

    public List<Point> asPoints() {
        List<Point> points = Arrays.<Point> asList(this);
        assert Util.checkNonNull(points) : "Null points in " + this;
        return points;
    }

    public void randomize(Random random) {
        if (roi == null) {
            cx = rdouble(random.nextInt(100));
            cy = rdouble(random.nextInt(100));
        } else {
            throw new UnsupportedOperationException(
                    "Roi-based values unsupported");
        }
    }
}