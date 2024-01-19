#!/bin/bash
if [ ! -f /etc/default/sequoiadb ];then
   echo "Not exist install!"
   exit 1
fi

. /etc/default/sequoiadb

if [ -f $INSTALL_DIR/bin/sdbstop ];then
   $INSTALL_DIR/bin/sdbstop --all
fi


items=$(grep "path" $INSTALL_DIR/conf/local/*/sdb.conf)
for item in ${items}
do
   path=$(echo $item|awk -F '=' '{print $2}')
   rm -rf ${path}
done

if [ -d $INSTALL_DIR/conf/local/ ];then
   rm -rf $INSTALL_DIR/conf/local/*
fi

if [ -f $INSTALL_DIR/bin/sdbcmtop ];then
   $INSTALL_DIR/bin/sdbcmtop
   test $? -ne 0 && echo "$INSTALL_DIR/bin/sdbcmtop exec failure!" && exit 1
fi

if [ -f $INSTALL_DIR/bin/sdbcmart ];then
   $INSTALL_DIR/bin/sdbcmart
   test $? -ne 0 && echo "$INSTALL_DIR/bin/sdbcmart exec failure!" && exit 1
fi

exit 0
