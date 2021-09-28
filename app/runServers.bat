cd src
javac -d ./ Client.java FrontEnd.java FrontEndInterface.java Replica.java ReplicaInterface.java Database.java

START /B rmiregistry 37001
timeout 5

cd ..

START runReplica.bat
START runReplica.bat
START runReplica.bat
START runReplica.bat

START runFrontEnd.bat

