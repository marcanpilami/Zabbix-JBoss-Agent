#!/bin/sh

##############################
##### Check and setup Env ####
##############################

ACTION=$1

# Go to lib directory
cd $(dirname $0)/../lib

START_JAR="connector.jar"  # Name of the executable Java archive file
WAITING_TIME=70    # Seconds to wait for gracefull shutdown

if [[ $(whoami) == "root" ]] && [[ $RUNASUSER == "" ]]
then
	echo "Defaulting to Unix account zabbix"
	RUNASUSER="zabbix"
fi

# Java extra options that can be used to customise memory settings
if [[ "${JAVA_OPTS}" == "" ]]
then
    JAVA_OPTS=" -Xms32m -Xmx128m -XX:MaxPermSize=32m "
fi

if [[ "${JAVA_HOME}" != "" ]]
then
    JAVA="${JAVA_HOME}/bin/java $JAVA_OPTS"
elif [[ "${RUNASUSER}" != "" ]] && [ $(sudo -u ${RUNASUSER} -H sh -c "which java >/dev/null 2>&1; echo \$?") -eq 0 ]
then
    JAVA=$(sudo -u ${RUNASUSER} -H sh -c "which java")
    JAVA="$JAVA $JAVA_OPTS"
elif [ -x /usr/java6_64/jre/ ]
then
    JAVA="/usr/java6_64/jre/bin/java $JAVA_OPTS"
else
    JAVA="$(which java) $JAVA_OPTS"
fi

$JAVA -version  > /dev/null 2>&1
if [[ $? -ne 0 ]]
then
        echo "No java found. Please define JAVA_HOME"
        exit 1
fi

if [[ "${CONFFILE}" != "" ]]
then
    CONFFILE=" -Dconfig=${CONFFILE} "
    II=$(echo ${CONFFILE} | md5sum | cut -d' ' -f1)
    echo "Will use configuration file option ${CONFFILE}"
fi

PID_FILE=../conf/app${II}.pid
LOG_OUT_FILE=../logs/connector_out${II}
LOG_ERR_FILE=../logs/connector_err${II}
STDOUT_NPIPE=../logs/stdout_$$.pipe
STDERR_NPIPE=../logs/stderr_$$.pipe
LOG_HISTORY=30  # Days of log history to keep

# Helper function to log output with rotation on time basis (per hour)
# Arg 1 is log file prefix
log_rotate() {
	while read i
    do
        echo "$i" >> $1_$(date +%Y.%m.%d-%H).log
    done
}

# Helper function to cleanup log pipes
remove_npipes() {
	rm ../logs/stdout_*.pipe logs/stderr_*.pipe > /dev/null 2>&1
}

#############################################
#### Start/Stop/Restart/Status functions ####
#############################################

app_start() {
	echo Starting application...
	test -e ${PID_FILE}
        if [[ $? -eq 0 ]]
        then
            echo "PID file found (${PID_FILE})."
            PID=$(cat ${PID_FILE})
        	ps -p $PID > /dev/null 2>&1
        	if [[ $? -ne 0 ]]
        	then
                	echo "PID file is here (${PID_FILE}) but daemon is gone..."
                	echo "Cleaning up pid file"
                	rm ${PID_FILE}
        	else
               		echo "Application is already running with PID ${PID}"
                	exit 1
        	fi
        fi
	# Remove old logs
	for LOG_FILE in $LOG_OUT_FILE $LOG_ERR_FILE
	do
		find ../logs -name "$(basename ${LOG_FILE})*.log" -mtime +${LOG_HISTORY} | xargs -r rm
	done
	# We can go on...
	if [[ $1 == "console" ]]
	then
		$JAVA ${CONFFILE} -jar $START_JAR 
	else
		remove_npipes
		mknod $STDOUT_NPIPE p
		mknod $STDERR_NPIPE p
		log_rotate <$STDOUT_NPIPE $LOG_OUT_FILE &
		log_rotate <$STDERR_NPIPE $LOG_ERR_FILE &
		exec 1> $STDOUT_NPIPE
		exec 2> $STDERR_NPIPE
		nohup $JAVA  ${CONFFILE} -jar $START_JAR  &
		PID=$!
		echo $PID > ${PID_FILE}
		echo "Application started with pid ${PID}"
	fi
}

app_stop() {
	echo Stopping application...
	test -e ${PID_FILE}
	if [[ $? -ne 0 ]]
	then
        echo "PID file not found (${PID_FILE})."
		echo "Here are all application instances running on this host, choose the good one and kill it yourself:"
		ps -ef | grep ${START_JAR} | grep -v grep
        exit 1
	fi
	PID=$(cat ${PID_FILE})
	echo "Sending SIGTERM to process $PID and waiting for graceful shutdown."
	GRACEFUL_KILL="no"
	kill $PID
	while [ $(( WAITING_TIME -= 1 )) -ge 0 ]
	do
		printf "."
		ps -p $PID > /dev/null 2>&1
		if [[ $? -ne 0 ]]
		then
			GRACEFUL_KILL="yes"
			break;
		fi
		sleep 1
	done
	echo ""
	if [[ $GRACEFUL_KILL == "no" ]]
	then
		echo "Application did not respond to SIGTERM. Killing (SIGKILL)..."
		kill -9 $PID
	else
		echo "Application has shutdown properly."
	fi
	rm ${PID_FILE}
	remove_npipes
}

app_status() {
	test -e ${PID_FILE}
	if [[ $? -ne 0 ]]
	then
       	echo "PID file not found (${PID_FILE})."
		echo "Here are all java processes running on this host"
		ps -ef |grep $START_JAR | grep -v grep
       	exit 1
	fi
	PID=$(cat ${PID_FILE})
	ps -p $PID > /dev/null 2>&1
	if [[ $? -ne 0 ]]
	then
		echo "PID file is here (${PID_FILE}) but daemon is gone..."
		echo "Cleaning up pid file"
		rm ${PID_FILE}
		remove_npipes
		exit 1
	else
		echo "Application is running with PID ${PID}"
	fi
}


###############################
##### Decide what to do... ####
###############################
case "$ACTION" in
	start)
		app_start
		;;
	startconsole)
		app_start "console"
		;;
	stop)
		app_stop
		;;
	restart)
		app_stop
		app_start
		;;
	status)
		app_status
		;;	
	*)
		echo "Usage: $0 {start|stop|restart|status}"
		;;
esac
