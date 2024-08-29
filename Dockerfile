FROM gradle:8.10-jdk17-alpine as builder

# Set arguments with defaults
ARG SCALA_VERSION=3.1.1
ARG WORKDIR=/app^

# Map arguments to environment
ENV SCALA_HOME="/usr/local/scala"
ENV SCALA_VERSION=$SCALA_VERSION
ENV WORKDIR=$WORKDIR

RUN apk add --no-cache --virtual=.build-dependencies wget ca-certificates \
    && apk add --no-cache bash \
    && cd "/tmp" \
    && wget --no-verbose https://github.com/lampepfl/dotty/releases/download/$SCALA_VERSION/scala3-$SCALA_VERSION.tar.gz \
    && tar xzf scala3-$SCALA_VERSION.tar.gz -C /tmp/ \
    && mkdir "${SCALA_HOME}" \
    && rm "/tmp/scala3-${SCALA_VERSION}/bin/"*.bat \
    && mv "/tmp/scala3-${SCALA_VERSION}/bin" "/tmp/scala3-${SCALA_VERSION}/lib" "${SCALA_HOME}" \
    && ln -s "${SCALA_HOME}/bin/"* "/usr/bin/" \
    && apk del .build-dependencies \
    && rm -rf "/tmp/"*

WORKDIR $WORKDIR

COPY . $WORKDIR

# build shadow jar
RUN gradle --refresh-dependencies --stacktrace clean shadowJar

# start over from a clean JRE image
FROM eclipse-temurin:17-jre-alpine as runner

ENV WORKDIR=/app

# set scala version and scala home
ENV SCALA_HOME="/usr/share/scala"
ENV SCALA_VERSION=3.1.1

# install scala
RUN apk add --no-cache --virtual=.build-dependencies wget ca-certificates \
    && apk add --no-cache bash \
    && cd "/tmp" \
    && wget --no-verbose https://github.com/lampepfl/dotty/releases/download/$SCALA_VERSION/scala3-$SCALA_VERSION.tar.gz \
    && tar xzf scala3-$SCALA_VERSION.tar.gz -C /tmp/ \
    && mkdir "${SCALA_HOME}" \
    && rm "/tmp/scala3-${SCALA_VERSION}/bin/"*.bat \
    && mv "/tmp/scala3-${SCALA_VERSION}/bin" "/tmp/scala3-${SCALA_VERSION}/lib" "${SCALA_HOME}" \
    && ln -s "${SCALA_HOME}/bin/"* "/usr/bin/" \
    && apk del .build-dependencies \
    && rm -rf "/tmp/"*

WORKDIR $WORKDIR

# copy shadow jar from previous build
COPY --from=builder \
    /app/build/libs/simple-prm-all.jar $WORKDIR

ENTRYPOINT ["java", "-cp" , "/app/simple-prm-all.jar", "com.github.johanneshiry.simpleprm.main.Run"]
