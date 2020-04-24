# WebDAV Service

## Notes

The PROPFIND method retrieves properties defined on the resource
identified by the Request-URI, if the resource does not have any
internal members, or on the resource identified by the Request-URI
and potentially its member resources, if the resource is a collection
that has internal member URLs.  __All DAV-compliant resources MUST
support the PROPFIND method and the propfind XML element
(Section 14.20) along with all XML elements defined for use with that
element.__

Servers MUST support "0" and "1" depth requests on WebDAV-compliant resources.

Servers SHOULD treat a request without a Depth header as if a "Depth: infinity" header was included.

An empty PROPFIND request body MUST be treated as if it were an 'allprop' request.

All servers MUST support returning a response of content type text/xml or application/xml that contains a multistatus 
XML element that describes the results of the attempts to retrieve the various properties.

