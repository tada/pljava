package org.postgresql.pljava.fdw;

import java.security.cert.Certificate;

/**
 * Placeholder for user information - it should use the
 * existing pl/java class.
 *
 * The effective and real usernames can be retrieved from
 * the OIDs.
 *
 * For sensitive material the java classes can implement
 * additional AuthN and AuthZ functionality on their own.
 * In this case it's also common for the AuthN and AuthZ
 * to consider both how the user was authenticated and
 * their apparent physical location.
 *
 * Some of this information is available if you have a
 * JDBC Connection... but the whole point of this module
 * is to hide the fact that this code is used by a database.
 * It will be much harder to break the database if the java
 * classes have absolutely no access to the connection, or
 * even any awareness of its existence.
 *
 * Kerberos notes:
 *
 * - The java code should have access to the user's
 *   Kerberos principal. I know the principal -> username
 *   mapping is in pg_ident.conf but the java code should
 *   not rely on it.
 *
 * - Reminder that Kerberos provides secure authentication
 *   but it does not provide secure transport. You must explicitly
 *   add TLS for an encrypted connection. This is often
 *   overlooked by people unfamiliar with Kerberos and is
 *   why we need an explicit check for a secure connection.
 */
public interface FDWUser {  // also implement Principal ??

	// is there an actual default user?...
	String DEFAULT_USER = "unknown_user";

	enum AuthenticationMechanism {
		TRUST,
		REJECT,
		MD5,
		PASSWORD,
		SCRAM_SHA_256,
		GSS, // Kerberos
		SSPI,
		IDENT,
		PEER,
		PAM,
		LDAP,
		RADIUS,
		CERT,
		UNKNOWN
	}

	/**
	 * The user's unique ID. It can be used to maintain a cache.
	 */
	default Long getOid() { return null; }

	/**
	 * The real user's unique ID. It can be used to maintain a cache.
	 */
	default Long getRealOid() { return null; }

	/**
	 * Get the effective database username.
	 * @return
	 */
	default String getUsername() {
		return getRealUsername();
	};

	/**
	 * Get the real database username.
	 * @return
	 */
	default String getRealUsername() {
		return DEFAULT_USER;
	}

	/**
	 * Is the connection secure?
	 *
	 * This may be superfluous since this information is
	 * already available via the `DatabaseMetaData`
	 * connection information. However I can't rule out
	 * the possibility of a desire to have more details
	 * about the connection, e.g., the algorithm used,
	 * the keysize, etc.
	 */
	default boolean isConnectionSecure()
	{
		return false;
	}

	/**
	 * Get the authentication mechanism used.
	 */
	default AuthenticationMechanism getAuthenticationMechanism()
	{
		return AuthenticationMechanism.UNKNOWN;
	}

	/**
	 * Get the user's location. (named socket, IP address + port)
	 */
	default Object getLocation() { return null; }

	/**
	 * Get the user's Certificate, if `cert` authentication was used.
	 */
	default Certificate getCertificate() { return null; }
}
