/*
 * Copyright (c) 2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.util.Iterator;

import javax.xml.namespace.QName;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import javax.xml.stream.events.*;

import javax.xml.stream.util.XMLEventConsumer;

/**
 * Consume a stream of StAX {@code XMLEvent}s, writing them to an
 * {@code XMLStreamWriter}.
 *<p>
 * This entire class would be completely unnecessary if not for the
 * regression in Java 9 and later that leaves
 * {@code XMLOutputFactory.createXMLEventWriter} throwing a
 * {@code ClassCastException} if passed a {@code StAXResult} wrapping an
 * arbitrary {@code XMLStreamWriter} implementation, which works perfectly
 * up through Java 8. Java 9 breaks it, demanding this soul-crushing workaround.
 *<p>
 * Making the best of a bad situation, in reimplementing this, it is possible to
 * honor the distinction between empty elements and start/end tags with nothing
 * in between. An {@code XMLStreamWriter} has distinct methods
 * {@code writeStartElement} and {@code writeEmptyElement}, but the
 * {@code XMLEventWriter} API offers no obvious way to pass along that
 * distinction. There is a nonobvious way, though, if the {@code StartElement}
 * and {@code EndElement} events have parser-supplied {@code Location}s, and
 * they are equal (and not 'unknown' in all values).
 */
class XMLEventToStreamConsumer
implements XMLEventConsumer, XMLStreamConstants
{
	protected final XMLStreamWriter m_xsw;
	protected StartElement m_startElement;
	protected Location m_location;

	/**
	 * Construct an {@code XMLEventToStreamConsumer} that writes to the
	 * given {@code XMLStreamWriter}.
	 */
	XMLEventToStreamConsumer(XMLStreamWriter xsw)
	{
		if ( null == xsw )
			throw new NullPointerException("XMLEventToStreamConsumer");
		m_xsw = xsw;
	}

	/**
	 * Dispatch an {@code XMLEvent} to the corresponding specialized
	 * {@code add} method.
	 */
	@Override
	public void add(XMLEvent event) throws XMLStreamException
	{
		if ( null == event )
			throw new NullPointerException("XMLEventToStreamConsumer.add");

		switch ( event.getEventType() )
		{
		case COMMENT:                add(              (Comment) event); break;
		case PROCESSING_INSTRUCTION: add((ProcessingInstruction) event); break;
		case CDATA:      // fallthrough
		case CHARACTERS:             add(           (Characters) event); break;
		case DTD:                    add(                  (DTD) event); break;
		case ENTITY_REFERENCE:       add(      (EntityReference) event); break;
		case START_DOCUMENT:         add(        (StartDocument) event); break;
		case END_DOCUMENT:           add(          (EndDocument) event); break;
		case START_ELEMENT:          add(         (StartElement) event); break;
		case END_ELEMENT:            add(           (EndElement) event); break;
		default:
			throw new XMLStreamException(
				"Unexpected XMLEvent type " + event.getEventType());
		}
	}

	protected void addNonEmptyIfCached() throws XMLStreamException
	{
		if ( null == m_startElement )
			return;
		add(m_startElement, false);
		m_startElement = null;
		m_location = null;
	}

	protected void add(Comment event) throws XMLStreamException
	{
		addNonEmptyIfCached();
		m_xsw.writeComment(event.getText());
	}

	protected void add(ProcessingInstruction event) throws XMLStreamException
	{
		addNonEmptyIfCached();
		m_xsw.writeProcessingInstruction(event.getTarget(), event.getData());
	}

	protected void add(Characters event) throws XMLStreamException
	{
		addNonEmptyIfCached();
		String content = event.getData();
		if ( event.isCData() )
			m_xsw.writeCData(content);
		else
			m_xsw.writeCharacters(content);
	}

	protected void add(DTD event) throws XMLStreamException
	{
		// no element precedes a DTD
		m_xsw.writeDTD(event.getDocumentTypeDeclaration());
	}

	protected void add(EntityReference event) throws XMLStreamException
	{
		addNonEmptyIfCached();
		m_xsw.writeEntityRef(event.getName());
	}

	protected void add(StartDocument event) throws XMLStreamException
	{
		String  version = event.getVersion();
		String encoding = event.getCharacterEncodingScheme();
		if ( event.encodingSet() )
			m_xsw.writeStartDocument(encoding, version);
		else
			m_xsw.writeStartDocument(version);
	}

	protected void add(EndDocument event) throws XMLStreamException
	{
		if ( null != m_startElement )
		{
			add(m_startElement, true);
			m_startElement = null;
			m_location = null;
		}
		m_xsw.writeEndDocument();
	}

	protected void add(StartElement event)
	throws XMLStreamException
	{
		addNonEmptyIfCached();
		m_startElement = event;
		m_location = event.getLocation();
	}

	protected void add(StartElement event, boolean empty)
	throws XMLStreamException
	{
		QName qn = event.getName();
		if ( empty )
			m_xsw.writeEmptyElement(
				qn.getPrefix(), qn.getLocalPart(), qn.getNamespaceURI());
		else
			m_xsw.writeStartElement(
				qn.getPrefix(), qn.getLocalPart(), qn.getNamespaceURI());
		for ( Namespace n : namespaces(event) )
			add(n);
		for ( Attribute a : attributes(event) )
			add(a);
	}

	protected void add(EndElement event) throws XMLStreamException
	{
		if ( null == m_startElement )
			m_xsw.writeEndElement();
		else
		{
			add(m_startElement, locationsEqual(m_location,event.getLocation()));
			m_startElement = null;
			m_location = null;
		}
	}

	protected void add(Attribute a) throws XMLStreamException
	{
		QName n = a.getName();
		m_xsw.writeAttribute(
			n.getPrefix(), n.getNamespaceURI(), n.getLocalPart(), a.getValue());
	}

	protected void add(Namespace n) throws XMLStreamException
	{
		m_xsw.writeNamespace(n.getPrefix(), n.getNamespaceURI());
	}

	protected static boolean locationsEqual(Location a, Location b)
	{
		if ( null == a  ||  null == b )
			return false;
		if ( ! locationIdsEqual(a.getPublicId(), b.getPublicId()) )
			return false;
		if ( ! locationIdsEqual(a.getSystemId(), b.getSystemId()) )
			return false;
		int aOffset = a.getCharacterOffset();
		if ( b.getCharacterOffset() != aOffset )
			return false;
		int aColumn = a.getColumnNumber();
		if ( b.getColumnNumber() != aColumn )
			return false;
		int aLine = a.getLineNumber();
		if ( b.getLineNumber() != aLine )
			return false;
		return ( -1 != aOffset ) || ( -1 != aColumn && -1 != aLine );
	}

	private static boolean locationIdsEqual(String a, String b)
	{
		if ( a == b )
			return true;
		if ( null != a )
			return a.equals(b);
		return b.equals(a);
	}

	/*
	 * XXX Do the below with lambdas once Java back horizon >= 8.
	 */
	protected Attributes attributes(StartElement event)
	{
		Attributes as = new Attributes();
		as.m_event = event;
		return as;
	}

	protected Namespaces namespaces(StartElement event)
	{
		Namespaces ns = new Namespaces();
		ns.m_event = event;
		return ns;
	}

	static abstract class ElementIterable<T> implements Iterable<T>
	{
		protected StartElement m_event;

		@Override
		public abstract Iterator<T> iterator();
	}

	static class Attributes extends ElementIterable<Attribute>
	{
		@Override
		public Iterator<Attribute> iterator()
		{
			return (Iterator<Attribute>)m_event.getAttributes();
		}
	}

	static class Namespaces extends ElementIterable<Namespace>
	{
		@Override
		public Iterator<Namespace> iterator()
		{
			return (Iterator<Namespace>)m_event.getNamespaces();
		}
	}
}
