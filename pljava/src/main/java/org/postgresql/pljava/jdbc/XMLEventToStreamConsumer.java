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
 */
class XMLEventToStreamConsumer
implements XMLEventConsumer, XMLStreamConstants
{
	private final XMLStreamWriter m_xsw;

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

	protected void add(Comment event) throws XMLStreamException
	{
		m_xsw.writeComment(event.getText());
	}

	protected void add(ProcessingInstruction event) throws XMLStreamException
	{
		m_xsw.writeProcessingInstruction(event.getTarget(), event.getData());
	}

	protected void add(Characters event) throws XMLStreamException
	{
		String content = event.getData();
		if ( event.isCData() )
			m_xsw.writeCData(content);
		else
			m_xsw.writeCharacters(content);
	}

	protected void add(DTD event) throws XMLStreamException
	{
		m_xsw.writeDTD(event.getDocumentTypeDeclaration());
	}

	protected void add(EntityReference event) throws XMLStreamException
	{
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
		m_xsw.writeEndDocument();
	}

	protected void add(StartElement event) throws XMLStreamException
	{
		QName qn = event.getName();
		m_xsw.writeStartElement(
			qn.getPrefix(), qn.getLocalPart(), qn.getNamespaceURI());
		for ( Namespace n : namespaces(event) )
			add(n);
		for ( Attribute a : attributes(event) )
			add(a);
	}

	protected void add(EndElement event) throws XMLStreamException
	{
		m_xsw.writeEndElement();
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
