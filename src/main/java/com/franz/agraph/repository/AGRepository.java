/******************************************************************************
** Copyright (c) 2008-2016 Franz Inc.
** All rights reserved. This program and the accompanying materials
** are made available under the terms of the Eclipse Public License v1.0
** which accompanies this distribution, and is available at
** http://www.eclipse.org/legal/epl-v10.html
******************************************************************************/

package com.franz.agraph.repository;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.NameValuePair;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.base.RepositoryBase;

import com.franz.agraph.http.AGHTTPClient;
import com.franz.agraph.http.AGHttpRepoClient;
import com.franz.agraph.http.exception.AGHttpException;
import com.franz.agraph.pool.AGConnPool;

/**
 * Implements the Sesame Repository interface for AllegroGraph, representing
 * <a href="http://www.franz.com/agraph/support/documentation/current/agraph-introduction.html#triple-store">triple-stores
 * on the server</a>.
 * In AllegroGraph, an {@link AGCatalog} contains multiple repositories.
 * With the Sesame API, most data operations on a repository are done through
 * the {@link AGRepositoryConnection} returned by {@link #getConnection()}.
 */
public class AGRepository extends RepositoryBase implements AGAbstractRepository {
	private final AGCatalog catalog;
	private final String repositoryID;
	private final String catalogPrefixedRepositoryID;
	private final String repositoryURL;
	private final AGValueFactory vf;
	private AGConnPool pool;

	/**
	 * Creates an AGRepository instance for a repository having the given
	 * repository id in the given catalog.
	 *
	 * <p>Preferred access is from {@link AGCatalog} methods
	 * such as {@link AGCatalog#createRepository(String, boolean)}
	 * or {@link AGCatalog#openRepository(String)}.
	 * </p>
	 * 
	 * @param catalog  the catalog in which to create the repository
	 * @param repositoryID  the name of the repository
	 */
	public AGRepository(AGCatalog catalog, String repositoryID) {
		this.catalog = catalog;
		this.repositoryID = repositoryID;
		catalogPrefixedRepositoryID = catalog.getCatalogPrefixedRepositoryID(repositoryID);
		repositoryURL = catalog.getRepositoryURL(repositoryID);
		vf = new AGValueFactory(this);
	}

	/**
	 * Gets the catalog to which this repository belongs.
	 *
	 * @return the catalog
	 */
	public AGCatalog getCatalog() {
		return catalog;
	}

	/**
	 * Gets the repository id for this repository.
	 *
	 * @return the repository id
	 */
	public String getRepositoryID() {
		return repositoryID;
	}

	public String getCatalogPrefixedRepositoryID() {
		return catalogPrefixedRepositoryID;
	}

	/**
	 * The AllegroGraph URL of this repository.
	 *
	 * @return the URL of this repository
	 */
	public String getRepositoryURL() {
		return repositoryURL;
	}

	public AGValueFactory getValueFactory() {
		return vf;
	}

	/**
	 * 
	 * @return {@link AGHTTPClient}  The http connection to AllegroGraph server  
	 */
	public AGHTTPClient getHTTPClient() {
		return getCatalog().getHTTPClient();
	}

	@Override
	protected void initializeInternal() throws RepositoryException {
	}

	/**
	 * Create a connection to the repository.
	 * @param executor Executor service used to schedule maintenance tasks,
	 *                 such as calling ping() periodically.
	 *                 Set to null to disable such tasks.
	 *                 Call {@link #getConnection()} to use the default executor
	 *                 specified by the server object.
	 *                 This argument is ignored if connection pooling is used
	 */
	public AGRepositoryConnection getConnection(ScheduledExecutorService executor)
			throws RepositoryException {
		if (pool!=null) {
			return pool.borrowConnection();
		} else {
			AGHttpRepoClient repoclient = new AGHttpRepoClient(
					this, getCatalog().getHTTPClient(), repositoryURL, null, executor);
			return new AGRepositoryConnection(this, repoclient);
		}
	}

	/**
	 * Create a connection to the repository.
	 */
	public AGRepositoryConnection getConnection() throws RepositoryException {
		return getConnection(catalog.getServer().getExecutor());
	}

	/**
	 * Returns true iff this repository is writable.
	 */
	public boolean isWritable() throws RepositoryException {
		String url = getCatalog().getRepositoriesURL();
		TupleQueryResult tqresult = getHTTPClient().getTupleQueryResult(url);
		Value writable = null;
		boolean result;
        try {
            while (null==writable && tqresult.hasNext()) {
                BindingSet bindingSet = tqresult.next();
                Value uri = bindingSet.getValue("uri");
                if (uri.stringValue().equals(getRepositoryURL())) {
                	writable = bindingSet.getValue("writable");
                }
            }
        } catch (QueryEvaluationException e) {
        	throw new RepositoryException(e);
        } finally {
            try {
				tqresult.close();
			} catch (QueryEvaluationException e) {
				throw new RepositoryException(e);
			}
        }
        if (null==writable) {
        	throw new IllegalStateException("Repository not found in catalog's list of repositories: " + getRepositoryURL());
        }
        result = Boolean.parseBoolean(writable.stringValue());
        return result;
	}

	public String getSpec() {
		String cname = getCatalog().getCatalogName(), name = getRepositoryID();
		if (AGCatalog.isRootID(cname)) return "<" + name + ">";
		else return "<" + cname + ":" + name + ">";
	}

	/**
	 * The dataDir is not currently applicable to AllegroGraph.
	 * @deprecated not applicable to AllegroGraph
	 */
	@Override
	public File getDataDir() {
		return null;
	}

	/**
	 * The dataDir is not currently applicable to AllegroGraph.
	 * @deprecated not applicable to AllegroGraph
	 */
	@Override
	public void setDataDir(File dataDir) {
		// TODO: consider using this for client-side logging
	}

	@Override
	protected void shutDownInternal() throws RepositoryException {
		if (pool!=null) pool.close();
	}

	/**
	 * Calls Sesame method {@link #shutDown()}.
	 */
    @Override
    public void close() throws RepositoryException {
        shutDown();
    }

    /**
     * Sets the repository's bulkMode (defaults to false).
     *
     * When in bulkMode, data can be added/loaded more quickly, but
     * there is no guarantee of durability in the event of a crash.
     * The bulkMode setting persists when the repository is closed.
     *
     * @param bulkMode  boolean indicating the intended bulkMode
     * @throws RepositoryException  if there is an error during the request
     * @see #isBulkMode()
     */
	public void setBulkMode(boolean bulkMode) throws RepositoryException {
		String url = repositoryURL + "/bulkMode";
		Header[] headers = new Header[0];
		NameValuePair[] data = {};
		try {
			if (bulkMode) {
				getHTTPClient().put(url, headers, data, null, null);
			} else {
				getHTTPClient().delete(url, headers, data, null);
			}
		} catch (AGHttpException e) {
			throw new RepositoryException(e);
		}
	}

	/**
	 * Returns the repository's bulkMode setting.
	 *
	 * @return Boolean  a value indicating the bulkMode setting
	 * @throws RepositoryException  if there is an error during the request
	 * @see #setBulkMode(boolean)
	 */
	public boolean isBulkMode() throws RepositoryException {
		try {
			return Boolean.parseBoolean(getHTTPClient().getString(repositoryURL+"/bulkMode"));
		} catch (AGHttpException e) {
			throw new RepositoryException(e);
		}
	}

	/**
	 * Sets the repository's duplicate suppression policy.
	 *
	 * This determines how/whether duplicates will be automatically removed at
	 * commit time.
	 *
	 * Legal policy names are "false" (turns automatic suppression off),
	 * "spo" (removes statements with the same s, p, and o), and "spog"
	 * (compares s, p, o, and g).
	 *
	 * For on-demand duplicate deletion, see
	 * {@link AGRepositoryConnection#deleteDuplicates(String)}.
	 *
	 * See also the protocol documentation for
	 * <a href="http://www.franz.com/agraph/support/documentation/current/http-protocol.html#get-suppress-duplicates">suppressing duplicates</a>.
	 * 
	 * @param policy name of the suppression policy to use
	 * @throws RepositoryException  if there is an error during the request
	 * @see #getDuplicateSuppressionPolicy()
	 */
	public void setDuplicateSuppressionPolicy(String policy) throws RepositoryException {
		String url = repositoryURL + "/suppressDuplicates";
		Header[] headers = new Header[0];
		NameValuePair[] data = {new NameValuePair("type",policy)};
		try {
			getHTTPClient().put(url, headers, data, null, null);
		} catch (AGHttpException e) {
			throw new RepositoryException(e);
		}
	}

	/**
	 * Returns the repository's duplicate suppression policy.
	 *
	 * @return the policy name
	 * @throws RepositoryException  if there is an error during the request
	 * @see #setDuplicateSuppressionPolicy(String)
	 */
	public String getDuplicateSuppressionPolicy() throws RepositoryException {
		try {
			return getHTTPClient().getString(repositoryURL+"/suppressDuplicates");
		} catch (AGHttpException e) {
			throw new RepositoryException(e);
		}
	}

	/**
	 * Sets the connection pool to use with this repository.
	 * <p>
	 * Enables the repository to use a connection pool so that Sesame
	 * apps can transparently benefit from connection pooling.  If pool
	 * is not null, getConnection() borrows a connection from the pool,
	 * and closing the connection returns it to the pool.  The pool is
	 * closed when the Repository is shutdown.
	 * </p>
	 * 
         * <pre>{@code
	 * Note that the AGConnPool parameters:
	 *
	 * 		AGConnProp.serverUrl, "http://localhost:10035",
	 * 		AGConnProp.username, "test",
	 * 		AGConnProp.password, "xyzzy",
	 * 		AGConnProp.catalog, "/",
	 * 		AGConnProp.repository, "my_repo",
	 *
	 * are currently assumed to match those of this repository.
	 * }</pre>
	 *
	 * @param pool  the pool to use with this repository
	 * @see AGConnPool
	 */
	public void setConnPool(AGConnPool pool) {
		this.pool = pool;
	}

    /**
     * Forces a checkpoint for this repository.
     *
     * This is an internal and undocumented function.
     * 
     * @throws RepositoryException  if there is an error during the request
     */
	public void forceCheckpoint() throws RepositoryException {
		String url = repositoryURL + "/force-checkpoint";
		Header[] hdr = new Header[0];
		NameValuePair[] data = {};
		getHTTPClient().post(url,hdr,data,null,null);
	}

    /**
     * Waits until background db processes have gone idle.
     *
     * This is an internal and undocumented function.
     * 
     * @throws RepositoryException  if there is an error during the request
     */
	public void ensureDBIdle() throws RepositoryException {
		String url = repositoryURL + "/ensure-db-idle";
		Header[] hdr = new Header[0];
		NameValuePair[] data = {};
		getHTTPClient().post(url,hdr,data,null,null);
	}
}