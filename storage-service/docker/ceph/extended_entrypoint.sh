./entrypoint.sh &
sleep 10 #hack
echo RUNNING SOME STUFF NOW!
ceph osd pool create irods 100 100 \
    && echo foo > foo.txt \
    && rados -p irods put foo foo.txt \
    && rm foo.txt \
    && ceph auth import -i keyring
echo Initialization done! Returning to entrypoint
wait %%
