var getHref = function(value, attr) {
	var doc = new DOMParser().parseFromString(value, "text/xml");
    return doc.firstChild.getAttribute('href');
};