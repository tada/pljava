# Building with a 32-bit Java Development Kit

When building with 32-bit Java, the following environment variable setting
before running Maven will reportedly prevent a stack overflow error during
the build:

    MAVEN_OPTS=-Xss1024k
