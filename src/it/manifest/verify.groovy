File manifest = new File( basedir, 'target/MANIFEST.MF' )
assert manifest.exists()

assert 1 == manifest.getText().count("Manifest-Version: 1.0")
assert 1 == manifest.getText().count("BuildInfo-Revision: ")
assert 1 == manifest.getText().count("BuildInfo-URL: ")
assert 1 == manifest.getText().count("Class-Path: resources/")
assert 1 == manifest.getText().count("BuildInfo-Timestamp: ")
assert 1 == manifest.getText().count("Main-Class: com.tc.cli.CommandLineMain")
assert 1 == manifest.getText().count("BuildInfo-Edition: opensource")
