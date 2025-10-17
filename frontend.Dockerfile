FROM httpd:2.4.62


COPY ./httpd.conf /usr/local/apache2/conf/httpd.conf

COPY ./src/main/resources/static /var/www
