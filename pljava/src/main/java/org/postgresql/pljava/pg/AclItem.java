/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.pg;

import static java.lang.Integer.lowestOneBit;
import static java.lang.Integer.numberOfTrailingZeros;

import java.lang.annotation.Native;
import java.util.List;

import java.nio.ByteBuffer;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;

import static
	org.postgresql.pljava.pg.CatalogObjectImpl.Factory.staticFormObjectId;

public abstract class AclItem implements CatalogObject.Grant
{
	@Native static final short ACL_INSERT	   = 1 <<  0;
	@Native static final short ACL_SELECT	   = 1 <<  1;
	@Native static final short ACL_UPDATE	   = 1 <<  2;
	@Native static final short ACL_DELETE	   = 1 <<  3;
	@Native static final short ACL_TRUNCATE    = 1 <<  4;
	@Native static final short ACL_REFERENCES  = 1 <<  5;
	@Native static final short ACL_TRIGGER     = 1 <<  6;
	@Native static final short ACL_EXECUTE     = 1 <<  7;
	@Native static final short ACL_USAGE	   = 1 <<  8;
	@Native static final short ACL_CREATE	   = 1 <<  9;
	@Native static final short ACL_CREATE_TEMP = 1 << 10;
	@Native static final short ACL_CONNECT     = 1 << 11;
	@Native static final   int N_ACL_RIGHTS    =	  12;
	@Native static final   int ACL_ID_PUBLIC   =	   0;

	@Native static final int OFFSET_ai_grantee  =  0;
	@Native static final int OFFSET_ai_grantor  =  4;
	@Native static final int OFFSET_ai_privs	=  8;
	@Native static final int SIZEOF_AclItem 	= 12;

	/**
	 * These one-letter abbreviations are to match the order of the bit masks
	 * declared above, following the {@code PRIVILEGE-ABBREVS-TABLE} in the
	 * PostgreSQL documentation, under Privileges, in the Data Definition
	 * chapter.
	 *<p>
	 * Note that the order of the table in the documentation need not match
	 * the order of the bits above. This string must be ordered like the bits.
	 */
	private static final String s_abbr = "arwdDxtXUCTc";

	static
	{
		assert N_ACL_RIGHTS == s_abbr.length() : "AclItem abbreviations";
		assert N_ACL_RIGHTS == s_abbr.codePoints().count() : "AclItem abbr BMP";
	}

	private final RegRole.Grantee m_grantee;
	private final int     m_grantor; // less often interesting

	protected AclItem(int grantee, int grantor)
	{
		m_grantee =
			(RegRole.Grantee) staticFormObjectId(RegRole.CLASSID, grantee);
		m_grantor = grantor;
	}

	@Override public RegRole.Grantee to()
	{
		return m_grantee;
	}

	@Override public RegRole by()
	{
		return staticFormObjectId(RegRole.CLASSID, m_grantor);
	}

	/**
	 * Implementation of all non-OnRole subinterfaces of Grant.
	 *<p>
	 * The distinct interfaces in the API are a type-safety veneer to help
	 * clients remember what privileges apply to what object types. Underneath,
	 * this class implements them all.
	 */
	public static class NonRole extends AclItem
	implements
		OnClass, OnNamespace,
		CatalogObject.EXECUTE, CatalogObject.CREATE_TEMP, CatalogObject.CONNECT
	{
		private final short m_priv;
		private final short m_goption;

		public NonRole(ByteBuffer b)
		{
			super(b.getInt(OFFSET_ai_grantee), b.getInt(OFFSET_ai_grantor));
			int privs = b.getInt(OFFSET_ai_privs);
			m_priv    = (short)(privs & 0xffff);
			m_goption = (short)(privs >>> 16);
		}

		private boolean priv(short mask)
		{
			return 0 != (m_priv & mask);
		}

		private boolean goption(short mask)
		{
			return 0 != (m_goption & mask);
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			if ( to().isValid() )
				sb.append(to().name());
			sb.append('=');
			int priv = Short.toUnsignedInt(m_priv);
			int goption = Short.toUnsignedInt(m_goption);
			while ( 0 != priv )
			{
				int bit = lowestOneBit(priv);
				priv ^= bit;
				sb.append(s_abbr.charAt(numberOfTrailingZeros(bit)));
				if ( 0 != (goption & bit) )
					sb.append('*');
			}
			sb.append('/').append(by().name());
			return sb.toString();
		}

		@Override public boolean selectGranted()
		{
			return priv(ACL_SELECT);
		}

		@Override public boolean selectGrantable()
		{
			return goption(ACL_SELECT);
		}

		@Override public boolean insertGranted()
		{
			return priv(ACL_INSERT);
		}

		@Override public boolean insertGrantable()
		{
			return goption(ACL_INSERT);
		}

		@Override public boolean updateGranted()
		{
			return priv(ACL_UPDATE);
		}

		@Override public boolean updateGrantable()
		{
			return goption(ACL_UPDATE);
		}

		@Override public boolean referencesGranted()
		{
			return priv(ACL_REFERENCES);
		}

		@Override public boolean referencesGrantable()
		{
			return goption(ACL_REFERENCES);
		}

		@Override public boolean deleteGranted()
		{
			return priv(ACL_DELETE);
		}

		@Override public boolean deleteGrantable()
		{
			return goption(ACL_DELETE);
		}

		@Override public boolean truncateGranted()
		{
			return priv(ACL_TRUNCATE);
		}

		@Override public boolean truncateGrantable()
		{
			return goption(ACL_TRUNCATE);
		}

		@Override public boolean triggerGranted()
		{
			return priv(ACL_TRIGGER);
		}

		@Override public boolean triggerGrantable()
		{
			return goption(ACL_TRIGGER);
		}

		@Override public boolean createGranted()
		{
			return priv(ACL_CREATE);
		}

		@Override public boolean createGrantable()
		{
			return goption(ACL_CREATE);
		}

		@Override public boolean usageGranted()
		{
			return priv(ACL_USAGE);
		}

		@Override public boolean usageGrantable()
		{
			return goption(ACL_USAGE);
		}

		@Override public boolean executeGranted()
		{
			return priv(ACL_EXECUTE);
		}

		@Override public boolean executeGrantable()
		{
			return goption(ACL_EXECUTE);
		}

		@Override public boolean create_tempGranted()
		{
			return priv(ACL_CREATE_TEMP);
		}

		@Override public boolean create_tempGrantable()
		{
			return goption(ACL_CREATE_TEMP);
		}

		@Override public boolean connectGranted()
		{
			return priv(ACL_CONNECT);
		}

		@Override public boolean connectGrantable()
		{
			return goption(ACL_CONNECT);
		}
	}
}
