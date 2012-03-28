/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.services.blitz.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import ome.api.IAdmin;
import ome.logic.HardWiredInterceptor;
import ome.model.internal.Permissions;
import ome.model.meta.Experimenter;
import ome.model.meta.ExperimenterGroup;
import ome.model.meta.Session;
import ome.security.SecuritySystem;
import ome.security.basic.PrincipalHolder;
import ome.services.blitz.fire.AopContextInitializer;
import ome.services.blitz.impl.AbstractAmdServant;
import ome.services.blitz.impl.AdminI;
import ome.services.blitz.impl.ConfigI;
import ome.services.blitz.impl.DeleteI;
import ome.services.blitz.impl.QueryI;
import ome.services.blitz.impl.ServiceFactoryI;
import ome.services.blitz.impl.ShareI;
import ome.services.blitz.impl.UpdateI;
import ome.services.blitz.util.BlitzExecutor;
import ome.services.scheduler.ThreadPool;
import ome.services.sessions.SessionManager;
import ome.services.util.Executor;
import ome.system.EventContext;
import ome.system.OmeroContext;
import ome.system.Principal;
import ome.system.ServiceFactory;
import ome.testing.InterceptingServiceFactory;
import ome.tools.spring.InternalServiceFactory;

/**
 * This fixture is copied from components/server/test/ome/server/itests/
 * Obviously copying is less clean then we would like, but for the moment,
 * sharing between test code is not supported in the ant build (Nov2008)
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since Beta4
 * @DEV.TODO Reunite with server code.
 */
public class ManagedContextFixture {

    public Ice.ObjectAdapter adapter;
    public OmeroContext ctx;
    public SessionManager mgr;
    public Executor ex;
    public ServiceFactoryI sf;
    public ServiceFactory managedSf;
    public ServiceFactory internalSf;
    public SecuritySystem security;
    public PrincipalHolder holder;
    public LoginInterceptor login;
    public AopContextInitializer init;
    public BlitzExecutor be;
    protected List<HardWiredInterceptor> cptors;

    // Servants
    public AdminI admin;
    public ConfigI config;
    public DeleteI delete;
    public QueryI query;
    public ShareI share;
    public UpdateI update;

    public ManagedContextFixture() throws Exception {
        this(OmeroContext.getManagedServerContext());
    }

    public void close() {
        ctx.closeAll();
    }

    public ManagedContextFixture(OmeroContext ctx) throws Exception {
       this(ctx, false);
    }

    /**
     * Create the fixture. Based on {@link #newUser} either creates a new
     * user or logins the root user.
     * @param ctx
     * @param newUser
     */
    public ManagedContextFixture(OmeroContext ctx, boolean newUser)
        throws Exception {
        this.ctx = ctx;
        be = (BlitzExecutor) ctx.getBean("throttlingStrategy");
        adapter = (Ice.ObjectAdapter) ctx.getBean("adapter");
        mgr = (SessionManager) ctx.getBean("sessionManager");
        ex = (Executor) ctx.getBean("executor");
        security = (SecuritySystem) ctx.getBean("securitySystem");
        holder = (PrincipalHolder) ctx.getBean("principalHolder");
        login = new LoginInterceptor(holder);
        managedSf = new ServiceFactory(ctx);
        managedSf = new InterceptingServiceFactory(managedSf, login);
        internalSf = new InternalServiceFactory(ctx);

        cptors = HardWiredInterceptor
            .parse(new String[] { "ome.security.basic.BasicSecurityWiring" });
        HardWiredInterceptor.configure(cptors, ctx);


        if (newUser) {
            loginNewUserNewGroup();
        } else {
            setCurrentUserAndGroup("root", "system");
        }
        sf = createServiceFactoryI();
        init = new AopContextInitializer(
                new ServiceFactory(ctx), login.p, new AtomicBoolean(true));
        delete = delete();
        update = new UpdateI(managedSf.getUpdateService(), be);
        query = new QueryI(managedSf.getQueryService(), be);
        admin = new AdminI(managedSf.getAdminService(), be);
        config = new ConfigI(managedSf.getConfigService(), be);
        share = new ShareI(managedSf.getShareService(), be);
        configure(delete, init);
        configure(update, init);
        configure(query, init);
        configure(admin, init);
        configure(config, init);
        configure(share, init);
    }

    private ServiceFactoryI createServiceFactoryI()
            throws omero.ApiUsageException {
        Ice.Current current = new Ice.Current();
        current.adapter = adapter;
        current.ctx = new HashMap<String, String>();
        current.ctx.put(omero.constants.CLIENTUUID.value, "my-client-uuid");
        ServiceFactoryI factory = new ServiceFactoryI(current, null, ctx, mgr, ex,
                getPrincipal(), new ArrayList<HardWiredInterceptor>(), null, null);
        return factory;
    }


    protected DeleteI delete() throws Exception {
        String out = ctx.getProperty("omero.threads.cancel_timeout");
        int timeout = Integer.valueOf(out);
        DeleteI d = new DeleteI(managedSf.getDeleteService(), be,
                ctx.getBean("threadPool", ThreadPool.class),
                timeout, ctx.getProperty("omero.data.dir"));
        d.setServiceFactory(sf);
        return d;
    }

    protected void configure(AbstractAmdServant servant,
            AopContextInitializer ini) {
        servant.setApplicationContext(ctx);
        servant.applyHardWiredInterceptors(cptors, ini);
    }

    public void tearDown() throws Exception {
        managedSf = null;
        ctx.close();
    }

    // UTILITIES
    // =========================================================================

    public String uuid() {
        return UUID.randomUUID().toString();
    }

    // LOGIN / PERMISSIONS
    // =========================================================================

    public long newGroup() {
        return newGroup(Permissions.USER_PRIVATE);
    }

    public long newGroup(Permissions permissions) {
        IAdmin admin = managedSf.getAdminService();
        String groupName = uuid();
        ExperimenterGroup g = new ExperimenterGroup();
        g.getDetails().setPermissions(permissions);
        g.setName(groupName);
        return admin.createGroup(g);
    }

    public void addUserToGroup(long user, long group) {
        addUserToGroup(user, group, false);
    }

    public void addUserToGroup(long user, long group, boolean admin) {
        final IAdmin iAdmin = managedSf.getAdminService();
        final Experimenter e = new Experimenter(user, false);
        final ExperimenterGroup g = new ExperimenterGroup(group, false);

        iAdmin.addGroups(e, g);
        if (admin) {
            iAdmin.addGroupOwners(g, e);
        }
    }

    /**
     * Create a new user in the given group
     */
    public String newUser(String group) {
        IAdmin admin = managedSf.getAdminService();
        Experimenter e = new Experimenter();
        String uuid = uuid();
        e.setOmeName(uuid);
        e.setFirstName("managed");
        e.setMiddleName("context");
        e.setLastName("test");
        admin.createUser(e, group);
        return uuid;
    }

    /**
     * Login a new user into a new group and return
     * 
     * @return
     */
    public String loginNewUserNewGroup() {
        IAdmin admin = managedSf.getAdminService();
        String groupName = uuid();
        ExperimenterGroup g = new ExperimenterGroup();
        g.setName(groupName);
        admin.createGroup(g);
        String name = newUser(groupName);
        setCurrentUser(name);
        return name;
    }

    public String getCurrentUser() {
        return managedSf.getAdminService().getEventContext()
                .getCurrentUserName();
    }

    public EventContext getCurrentEventContext() {
        return managedSf.getAdminService().getEventContext();
    }

    public void setCurrentUser(String user) {
        setCurrentUserAndGroup(user, "user");
    }
    
    public void setCurrentUserAndGroup(String user, String group) {
        Principal p = new Principal(user, group, "Test");
        Session s = mgr.createWithAgent(p, "ManagedFixture");
        p = new Principal(s.getUuid(), group, "Test");
        login.p = p;
    }

    public Principal getPrincipal() {
        return login.p;
    }
}
