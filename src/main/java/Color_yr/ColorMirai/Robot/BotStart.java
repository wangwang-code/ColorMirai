package Color_yr.ColorMirai.Robot;

import Color_yr.ColorMirai.EventDo.EventBase;
import Color_yr.ColorMirai.EventDo.EventCall;
import Color_yr.ColorMirai.Pack.ReturnPlugin.FriendsPack;
import Color_yr.ColorMirai.Pack.ReturnPlugin.GroupsPack;
import Color_yr.ColorMirai.Pack.ReturnPlugin.MemberInfoPack;
import Color_yr.ColorMirai.Pack.ToPlugin.*;
import Color_yr.ColorMirai.Socket.Plugins;
import Color_yr.ColorMirai.Socket.SendPackTask;
import Color_yr.ColorMirai.Socket.SocketServer;
import Color_yr.ColorMirai.Start;
import com.alibaba.fastjson.JSON;
import kotlin.coroutines.CoroutineContext;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactoryJvm;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.GroupSettings;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.Events;
import net.mamoe.mirai.event.ListeningStatus;
import net.mamoe.mirai.event.SimpleListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.FriendMessageEvent;
import net.mamoe.mirai.message.GroupMessageEvent;
import net.mamoe.mirai.message.TempMessageEvent;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.BotConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class BotStart {

    private static final List<SendPackTask> Tasks = new CopyOnWriteArrayList<>();
    private static final Base64.Decoder decoder = Base64.getDecoder();
    private static final Map<Long, MessageCall> MessageLsit = new ConcurrentHashMap<>();
    private static final List<Long> reList = new CopyOnWriteArrayList<>();

    private static ScheduledExecutorService service;
    private static Bot bot;
    private static Thread EventDo;
    private static boolean isRun;

    public static boolean Start() {
        bot = BotFactoryJvm.newBot(Start.Config.QQ, Start.Config.Password, new BotConfiguration() {
            {
                fileBasedDeviceInfo(Start.RunDir + "info.json");
                switch (Start.Config.Type) {
                    case 0:
                        setProtocol(MiraiProtocol.ANDROID_PHONE);
                        break;
                    case 1:
                        setProtocol(MiraiProtocol.ANDROID_WATCH);
                        break;
                }
            }
        });
        try {
            bot.login();
        } catch (Exception e) {
            Start.logger.error("机器人错误", e);
            return false;
        }

        Events.registerEvents(bot, new SimpleListenerHost() {
            //1 [机器人]图片上传前. 可以阻止上传（事件）
            @EventHandler
            public ListeningStatus BeforeImageUploadEvent(BeforeImageUploadEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                String name = event.getSource().toString();
                long id = event.getTarget().getId();
                var pack = new BeforeImageUploadPack(name, id);
                Tasks.add(new SendPackTask(1, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //2 [机器人]头像被修改（通过其他客户端修改了头像）（事件）
            @EventHandler
            public ListeningStatus BotAvatarChangedEvent(BotAvatarChangedEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                String name = event.getBot().getNick();
                var pack = new BotAvatarChangedPack(name);
                Tasks.add(new SendPackTask(2, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //3 [机器人]在群里的权限被改变. 操作人一定是群主（事件）
            @EventHandler
            public ListeningStatus BotGroupPermissionChangeEvent(BotGroupPermissionChangeEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                String name = event.getNew().name();
                var pack = new BotGroupPermissionChangePack(name, id);
                Tasks.add(new SendPackTask(3, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //4 [机器人]被邀请加入一个群（事件）
            @EventHandler
            public ListeningStatus BotInvitedJoinGroupRequestEvent(BotInvitedJoinGroupRequestEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroupId();
                String name = event.getInvitor().getNick();
                long fid = event.getInvitorId();
                long eventid = EventCall.AddEvent(new EventBase(event.getEventId(), (byte) 4, event));
                var pack = new BotInvitedJoinGroupRequestEventPack(name, id, fid, eventid);
                Tasks.add(new SendPackTask(4, JSON.toJSONString(pack)));

                return ListeningStatus.LISTENING;
            }

            //5 [机器人]成功加入了一个新群（不确定. 可能是主动加入）（事件）
            @EventHandler
            public ListeningStatus BotJoinGroupEventA(BotJoinGroupEvent.Active event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                var pack = new BotJoinGroupEventAPack(id);
                Tasks.add(new SendPackTask(5, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //6 [机器人]成功加入了一个新群（机器人被一个群内的成员直接邀请加入了群）（事件）
            @EventHandler
            public ListeningStatus BotJoinGroupEventB(BotJoinGroupEvent.Invite event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getInvitor().getId();
                String name = event.getInvitor().getNick();
                var pack = new BotJoinGroupEventBPack(name, id, fid);
                Tasks.add(new SendPackTask(6, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //7 [机器人]主动退出一个群（事件）
            @EventHandler
            public ListeningStatus BotLeaveEventA(BotLeaveEvent.Active event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                var pack = new BotLeaveEventAPack(id);
                Tasks.add(new SendPackTask(7, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //8 [机器人]被管理员或群主踢出群（事件）
            @EventHandler
            public ListeningStatus BotLeaveEventB(BotLeaveEvent.Kick event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                String name = event.getOperator().getNick();
                long fid = event.getOperator().getId();
                var pack = new BotLeaveEventBPack(name, id, fid);
                Tasks.add(new SendPackTask(7, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //9 [机器人]被禁言（事件）
            @EventHandler
            public ListeningStatus BotMuteEvent(BotMuteEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                int time = event.getDurationSeconds();
                String name = event.getOperator().getNick();
                long fid = event.getOperator().getId();
                var pack = new BotMuteEventPack(name, id, fid, time);
                Tasks.add(new SendPackTask(9, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //10 [机器人]主动离线（事件）
            @EventHandler
            public ListeningStatus BotOfflineEventA(BotOfflineEvent.Active event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                String message = event.getCause().getMessage();
                var pack = new BotOfflineEventAPack(message);
                Tasks.add(new SendPackTask(10, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //11 [机器人]被挤下线（事件）
            @EventHandler
            public ListeningStatus BotOfflineEventB(BotOfflineEvent.Force event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                String title = event.getTitle();
                String message = event.getMessage();
                var pack = new BotOfflineEventBPack(message, title);
                Tasks.add(new SendPackTask(11, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //12 [机器人]被服务器断开（事件）
            @EventHandler
            public ListeningStatus BotOfflineEventC(BotOfflineEvent.MsfOffline event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                String message = event.getCause().getMessage();
                var pack = new BotOfflineEventAPack(message);
                Tasks.add(new SendPackTask(12, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //13 [机器人]因网络问题而掉线（事件）
            @EventHandler
            public ListeningStatus BotOfflineEventD(BotOfflineEvent.Dropped event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                String message = event.getCause().getMessage();
                var pack = new BotOfflineEventAPack(message);
                Tasks.add(new SendPackTask(13, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //14 [机器人]服务器主动要求更换另一个服务器（事件）
            @EventHandler
            public ListeningStatus BotOfflineEventE(BotOfflineEvent.RequireReconnect event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getBot().getId();
                var pack = new BotOfflineEventCPack(id);
                Tasks.add(new SendPackTask(14, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //15 [机器人]登录完成, 好友列表, 群组列表初始化完成（事件）
            @EventHandler
            public ListeningStatus BotOnlineEvent(BotOnlineEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getBot().getId();
                var pack = new BotOnlineEventPack(id);
                Tasks.add(new SendPackTask(15, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //16 [机器人]主动或被动重新登录（事件）
            @EventHandler
            public ListeningStatus BotReloginEvent(BotReloginEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                String message = event.getCause().getMessage();
                var pack = new BotReloginEventPack(message);
                Tasks.add(new SendPackTask(16, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //17 [机器人]被取消禁言（事件）
            @EventHandler
            public ListeningStatus BotUnmuteEvent(BotUnmuteEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getOperator().getId();
                var pack = new BotUnmuteEventPack(id, fid);
                Tasks.add(new SendPackTask(17, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //18 [机器人]成功添加了一个新好友（事件）
            @EventHandler
            public ListeningStatus FriendAddEvent(FriendAddEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getFriend().getId();
                String name = event.getFriend().getNick();
                var pack = new FriendAddEventPack(name, id);
                Tasks.add(new SendPackTask(18, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //19 [机器人]好友头像被修改（事件）
            @EventHandler
            public ListeningStatus FriendAvatarChangedEvent(FriendAvatarChangedEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getFriend().getId();
                String name = event.getFriend().getNick();
                String url = event.getFriend().getAvatarUrl();
                var pack = new FriendAvatarChangedEventPack(name, id, url);
                Tasks.add(new SendPackTask(19, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //20 [机器人]好友已被删除（事件）
            @EventHandler
            public ListeningStatus FriendDeleteEvent(FriendDeleteEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getFriend().getId();
                String name = event.getFriend().getNick();
                var pack = new FriendDeleteEventPack(name, id);
                Tasks.add(new SendPackTask(20, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //21 [机器人]在好友消息发送后广播（事件）
            @EventHandler
            public ListeningStatus FriendMessagePostSendEvent(FriendMessagePostSendEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getTarget().getId();
                String name = event.getTarget().getNick();
                boolean res = event.getReceipt() != null;
                MessageSource message = event.getReceipt().getSource();
                String error = "";
                if (event.getException() != null) {
                    error = event.getException().getMessage();
                }
                var pack = new FriendMessagePostSendEventPack(message, id, name, res, error);
                Tasks.add(new SendPackTask(21, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //22 [机器人]在发送好友消息前广播（事件）
            @EventHandler
            public ListeningStatus FriendMessagePreSendEvent(FriendMessagePreSendEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                Message message = event.getMessage();
                long id = event.getTarget().getId();
                String name = event.getTarget().getNick();
                var pack = new FriendMessagePreSendEventPack(message, id, name);
                Tasks.add(new SendPackTask(22, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //23 [机器人]好友昵称改变（事件）
            @EventHandler
            public ListeningStatus FriendRemarkChangeEvent(FriendRemarkChangeEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getFriend().getId();
                String name = event.getNewName();
                var pack = new FriendRemarkChangeEventPack(id, name);
                Tasks.add(new SendPackTask(23, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //24 [机器人]群 "匿名聊天" 功能状态改变（事件）
            @EventHandler
            public ListeningStatus GroupAllowAnonymousChatEvent(GroupAllowAnonymousChatEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = 0;
                if (event.getOperator() != null) {
                    fid = event.getOperator().getId();
                }
                boolean old = event.getOrigin();
                boolean new_ = event.getNew();
                var pack = new GroupAllowAnonymousChatEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(24, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //25 [机器人]群 "坦白说" 功能状态改变（事件）
            @EventHandler
            public ListeningStatus GroupAllowConfessTalkEvent(GroupAllowConfessTalkEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                boolean old = event.getOrigin();
                boolean new_ = event.getNew();
                boolean bot = event.isByBot();
                var pack = new GroupAllowConfessTalkEventPack(id, old, new_, bot);
                Tasks.add(new SendPackTask(25, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //26 [机器人]群 "允许群员邀请好友加群" 功能状态改变（事件）
            @EventHandler
            public ListeningStatus GroupAllowMemberInviteEvent(GroupAllowMemberInviteEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = 0;
                if (event.getOperator() != null) {
                    fid = event.getOperator().getId();
                }
                boolean old = event.getOrigin();
                boolean new_ = event.getNew();
                var pack = new GroupAllowMemberInviteEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(26, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //27 [机器人]入群公告改变（事件）
            @EventHandler
            public ListeningStatus GroupEntranceAnnouncementChangeEvent(GroupEntranceAnnouncementChangeEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = 0;
                if (event.getOperator() != null) {
                    fid = event.getOperator().getId();
                }
                String old = event.getOrigin();
                String new_ = event.getNew();
                var pack = new GroupEntranceAnnouncementChangeEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(27, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //28 [机器人]在群消息发送后广播（事件）
            @EventHandler
            public ListeningStatus GroupMessagePostSendEvent(GroupMessagePostSendEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getTarget().getId();
                boolean res = event.getReceipt() != null;
                MessageSource message = event.getReceipt().getSource();
                String error = "";
                if (event.getException() != null) {
                    error = event.getException().getMessage();
                }
                var pack = new GroupMessagePostSendEventPack(id, res, message, error);
                Tasks.add(new SendPackTask(28, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //29 [机器人]在发送群消息前广播（事件）
            @EventHandler
            public ListeningStatus GroupMessagePreSendEvent(GroupMessagePreSendEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getTarget().getId();
                Message message = event.getMessage();
                var pack = new GroupMessagePreSendEventPack(id, message);
                Tasks.add(new SendPackTask(29, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //30 [机器人]群 "全员禁言" 功能状态改变（事件）
            @EventHandler
            public ListeningStatus GroupMuteAllEvent(GroupMuteAllEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = 0;
                if (event.getOperator() != null) {
                    fid = event.getOperator().getId();
                }
                boolean old = event.getOrigin();
                boolean new_ = event.getNew();
                var pack = new GroupMuteAllEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(30, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //31 [机器人]群名改变（事件）
            @EventHandler
            public ListeningStatus GroupNameChangeEvent(GroupNameChangeEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = 0;
                if (event.getOperator() != null) {
                    fid = event.getOperator().getId();
                }
                String old = event.getOrigin();
                String new_ = event.getNew();
                var pack = new GroupNameChangeEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(31, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //32 [机器人]图片上传成功（事件）
            @EventHandler
            public ListeningStatus ImageUploadEventA(ImageUploadEvent.Succeed event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getTarget().getId();
                String name = event.getImage().getImageId();
                var pack = new ImageUploadEventAPack(id, name);
                Tasks.add(new SendPackTask(32, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //33 [机器人]图片上传失败（事件）
            @EventHandler
            public ListeningStatus ImageUploadEventB(ImageUploadEvent.Failed event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getTarget().getId();
                String name = event.getSource().toString();
                String error = event.getMessage();
                int index = event.getErrno();
                var pack = new ImageUploadEventBPack(id, name, error, index);
                Tasks.add(new SendPackTask(33, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //34 [机器人]成员群名片改动（事件）
            @EventHandler
            public ListeningStatus MemberCardChangeEvent(MemberCardChangeEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                String old = event.getOrigin();
                String new_ = event.getNew();
                var pack = new MemberCardChangeEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(34, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //35 [机器人]成成员被邀请加入群（事件）
            @EventHandler
            public ListeningStatus MemberJoinEventA(MemberJoinEvent.Invite event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                String name = event.getMember().getNameCard();
                var pack = new MemberJoinEventAPack(id, fid, name);
                Tasks.add(new SendPackTask(35, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //36 [机器人]成员主动加入群（事件）
            @EventHandler
            public ListeningStatus MemberJoinEventB(MemberJoinEvent.Active event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                String name = event.getMember().getNameCard();
                var pack = new MemberJoinEventAPack(id, fid, name);
                Tasks.add(new SendPackTask(36, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //37 [机器人]一个账号请求加入群事件, [Bot] 在此群中是管理员或群主.（事件）
            @EventHandler
            public ListeningStatus MemberJoinRequestEvent(MemberJoinRequestEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getFromId();
                String message = event.getMessage();
                long eventid = EventCall.AddEvent(new EventBase(event.getEventId(), 37, event));
                var pack = new MemberJoinRequestEventPack(id, fid, message, eventid);
                Tasks.add(new SendPackTask(37, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //38 [机器人]成员被踢出群（事件）
            @EventHandler
            public ListeningStatus MemberLeaveEventA(MemberLeaveEvent.Kick event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                String fname = event.getMember().getNameCard();
                String ename = "";
                long eid = 0;
                if (event.getOperator() != null) {
                    eid = event.getOperator().getId();
                    ename = event.getOperator().getNameCard();
                }
                var pack = new MemberLeaveEventAPack(id, fid, eid, fname, ename);
                Tasks.add(new SendPackTask(38, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //39 [机器人]成员主动离开（事件）
            @EventHandler
            public ListeningStatus MemberLeaveEventB(MemberLeaveEvent.Quit event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                String name = event.getMember().getNameCard();
                var pack = new MemberLeaveEventBPack(id, fid, name);
                Tasks.add(new SendPackTask(39, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //40 [机器人]群成员被禁言（事件）
            @EventHandler
            public ListeningStatus MemberMuteEvent(MemberMuteEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                long eid = 0;
                String fname = event.getMember().getNameCard();
                String ename = "";
                int time = event.getDurationSeconds();
                if (event.getOperator() != null) {
                    eid = event.getOperator().getId();
                    ename = event.getOperator().getNameCard();
                }
                var pack = new MemberMuteEventPack(id, fid, eid, fname, ename, time);
                Tasks.add(new SendPackTask(40, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //41 [机器人]成员权限改变（事件）
            @EventHandler
            public ListeningStatus MemberPermissionChangeEvent(MemberPermissionChangeEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                String old = event.getOrigin().name();
                String new_ = event.getNew().name();
                var pack = new MemberPermissionChangeEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(41, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //42 [机器人]成员群头衔改动（事件）
            @EventHandler
            public ListeningStatus MemberSpecialTitleChangeEvent(MemberSpecialTitleChangeEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                String old = event.getOrigin();
                String new_ = event.getNew();
                var pack = new MemberSpecialTitleChangeEventPack(id, fid, old, new_);
                Tasks.add(new SendPackTask(42, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //43 [机器人]群成员被取消禁言（事件）
            @EventHandler
            public ListeningStatus MemberUnmuteEvent(MemberUnmuteEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getMember().getId();
                long eid = 0;
                String fname = event.getMember().getNameCard();
                String ename = "";
                if (event.getOperator() != null) {
                    eid = event.getOperator().getId();
                    ename = event.getOperator().getNameCard();
                }
                var pack = new MemberUnmuteEventPack(id, fid, eid, fname, ename);
                Tasks.add(new SendPackTask(43, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //44 [机器人]好友消息撤回（事件）
            @EventHandler
            public ListeningStatus MessageRecallEventA(MessageRecallEvent.FriendRecall event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getAuthorId();
                int mid = event.getMessageId();
                int time = event.getMessageTime();
                var pack = new MessageRecallEventAPack(id, mid, time);
                Tasks.add(new SendPackTask(44, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //45 [机器人]群消息撤回事件（事件）
            @EventHandler
            public ListeningStatus MessageRecallEventB(MessageRecallEvent.GroupRecall event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getAuthorId();
                int mid = event.getMessageId();
                int time = event.getMessageTime();
                long oid = 0;
                String oanme = "";
                if (event.getOperator() != null) {
                    oid = event.getOperator().getId();
                    oanme = event.getOperator().getNameCard();
                }
                var pack = new MessageRecallEventBPack(id, fid, mid, time, oid, oanme);
                Tasks.add(new SendPackTask(45, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //46 [机器人]一个账号请求添加机器人为好友（事件）
            @EventHandler
            public ListeningStatus NewFriendRequestEvent(NewFriendRequestEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getFromGroupId();
                long fid = event.getFromId();
                String name = event.getFromNick();
                String message = event.getMessage();
                long eventid = EventCall.AddEvent(new EventBase(event.getEventId(), 46, event));
                var pack = new NewFriendRequestEventPack(id, fid, name, message, eventid);
                Tasks.add(new SendPackTask(46, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //47 [机器人]在群临时会话消息发送后广播（事件）
            @EventHandler
            public ListeningStatus TempMessagePostSendEvent(TempMessagePostSendEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getTarget().getId();
                boolean res = event.getReceipt() != null;
                MessageSource message = event.getReceipt().getSource();
                String error = "";
                if (event.getException() != null) {
                    error = event.getException().getMessage();
                }
                var pack = new TempMessagePostSendEventPack(id, fid, res, message, error);
                Tasks.add(new SendPackTask(47, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //48 [机器人]在发送群临时会话消息前广播（事件）
            @EventHandler
            public ListeningStatus TempMessagePreSendEvent(TempMessagePreSendEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getTarget().getId();
                String fname = event.getTarget().getNameCard();
                Message message = event.getMessage();
                var pack = new TempMessagePreSendEventPack(id, fid, message, fname);
                Tasks.add(new SendPackTask(48, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //49 [机器人]收到群消息（事件）
            @EventHandler
            public ListeningStatus GroupMessageEvent(GroupMessageEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getSubject().getId();
                long fid = event.getSender().getId();
                String name = event.getSender().getNameCard();
                MessageChain message = event.getMessage();
                var call = new MessageCall();
                call.source = event.getSource();
                call.time = -1;
                call.id = call.source.getId();
                MessageLsit.put(call.id, call);
                var pack = new GroupMessageEventPack(id, fid, name, message);
                Tasks.add(new SendPackTask(49, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //50 [机器人]收到群临时会话消息（事件）
            @EventHandler
            public ListeningStatus TempMessageEvent(TempMessageEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getGroup().getId();
                long fid = event.getSender().getId();
                String name = event.getSenderName();
                MessageChain message = event.getMessage();
                var call = new MessageCall();
                call.source = event.getSource();
                call.time = -1;
                call.id = call.source.getId();
                MessageLsit.put(call.id, call);
                int time = event.getTime();
                var pack = new TempMessageEventPack(id, fid, name, message, time);
                Tasks.add(new SendPackTask(50, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //51 [机器人]收到朋友消息（事件）
            @EventHandler
            public ListeningStatus FriendMessageEvent(FriendMessageEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getSender().getId();
                String name = event.getSenderName();
                MessageChain message = event.getMessage();
                var call = new MessageCall();
                call.source = event.getSource();
                call.time = -1;
                call.id = call.source.getId();
                MessageLsit.put(call.id, call);
                int time = event.getTime();
                var pack = new FriendMessageEventPack(id, name, message, time);
                Tasks.add(new SendPackTask(51, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //72 [机器人]友输入状态改变（事件）
            public ListeningStatus FriendInputStatusChangedEvent(FriendInputStatusChangedEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getFriend().getId();
                String name = event.getFriend().getNick();
                boolean input = event.getInputting();
                var pack = new FriendInputStatusChangedEventPack(id, name, input);
                Tasks.add(new SendPackTask(72, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //73 [机器人]好友昵称改变（事件）
            public ListeningStatus FriendNickChangedEvent(FriendNickChangedEvent event) {
                if (SocketServer.havePlugin())
                    return ListeningStatus.LISTENING;
                long id = event.getFriend().getId();
                String old = event.getFrom();
                String new_ = event.getTo();
                var pack = new FriendNickChangedEventPack(id, old, new_);
                Tasks.add(new SendPackTask(73, JSON.toJSONString(pack)));
                return ListeningStatus.LISTENING;
            }

            //处理在处理事件中发生的未捕获异常
            @Override
            public void handleException(@NotNull CoroutineContext context, @NotNull Throwable exception) {
                Start.logger.error("在事件处理中发生异常" + "\n" + context, exception);
            }
        });

        isRun = true;
        EventDo = new Thread(() -> {
            while (isRun) {
                try {
                    if (!Tasks.isEmpty()) {
                        var task = Tasks.remove(0);
                        task.data += " ";
                        var temp = task.data.getBytes(StandardCharsets.UTF_8);
                        temp[temp.length - 1] = task.index;
                        for (Plugins item : SocketServer.PluginList.values()) {
                            item.callEvent(task.index, temp);
                        }
                    }
                    Thread.sleep(50);
                } catch (Exception e) {
                    Start.logger.error("插件处理事件出现问题", e);
                }
            }
        });
        EventDo.start();
        service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(() -> {
            if (!reList.isEmpty()) {
                for (var item : reList) {
                    if (MessageLsit.containsKey(item)) {
                        var item1 = MessageLsit.remove(item);
                        if (item1.time > 0 || item1.time == -1)
                            try {
                                bot.recall(item1.source);
                            } catch (Exception e) {
                                Start.logger.error("消息撤回失败", e);
                            }
                    }
                }
                reList.clear();
            }
            for (var item : MessageLsit.entrySet()) {
                MessageCall call = item.getValue();
                if (call.time > 0)
                    call.time--;
                if (call.time != 0) {
                    MessageLsit.put(item.getKey(), call);
                }
            }
            if (MessageLsit.size() >= Start.Config.MaxList) {
                MessageLsit.clear();
            }
        }, 0, 1, TimeUnit.SECONDS);
        Start.logger.info("机器人已启动");
        return true;
    }
    public static void addTask(SendPackTask task) {
        Tasks.add(task);
    }

    public static void stop() {
        try {
            isRun = false;
            if (bot != null)
                bot.close(new Throwable());
            if (EventDo != null)
                EventDo.join();
        } catch (Exception e) {
            Start.logger.error("关闭机器人时出现错误", e);
        }
    }

    public static void sendGroupMessage(long group, List<String> message) {
        try {
            Group group1 = bot.getGroup(group);
            MessageChain messageChain = MessageUtils.newChain("");
            for (var item : message) {
                if (item.startsWith("at:")) {
                    Member member = group1.get(Long.parseLong(item.replace("at:", "")));
                    messageChain = messageChain.plus(new At(member));
                } else if (item.startsWith("quote:")) {
                    var id = Long.parseLong(item.replace("quote:", ""));
                    MessageCall call = MessageLsit.get(id);
                    if (call == null)
                        continue;
                    if (call.source == null)
                        continue;
                    var quote = new QuoteReply(call.source);
                    messageChain = messageChain.plus(quote);
                } else {
                    messageChain = messageChain.plus(item);
                }
            }
            MessageSource source = group1.sendMessage(messageChain).getSource();
            if (source.getId() != -1) {
                var call = new MessageCall();
                call.source = source;
                call.time = 120;
                call.id = source.getId();
                MessageLsit.put(call.id, call);
            }
        } catch (Exception e) {
            Start.logger.error("发送群消息失败", e);
        }
    }

    public static void sendGroupPrivateMessage(long group, long fid, List<String> message) {
        try {
            Group group1 = bot.getGroup(group);
            MessageChain messageChain = MessageUtils.newChain("");
            for (var item : message) {
                messageChain = messageChain.plus(item);
            }
            MessageSource source = group1.get(fid).sendMessage(messageChain).getSource();
            if (source.getId() != -1) {
                var call = new MessageCall();
                call.source = source;
                call.time = 120;
                call.id = source.getId();
                MessageLsit.put(call.id, call);
            }
        } catch (Exception e) {
            Start.logger.error("发送群私聊消息失败", e);
        }
    }

    public static void sendFriendMessage(long fid, List<String> message) {
        try {
            MessageChain messageChain = MessageUtils.newChain("");
            for (var item : message) {
                messageChain = messageChain.plus(item);
            }
            MessageSource source = bot.getFriend(fid).sendMessage(messageChain).getSource();
            if (source.getId() != -1) {
                var call = new MessageCall();
                call.source = source;
                call.time = 120;
                call.id = source.getId();
                MessageLsit.put(call.id, call);
            }
        } catch (Exception e) {
            Start.logger.error("发送朋友消息失败", e);
        }
    }

    public static List<GroupsPack> getGroups() {
        var list = new ArrayList<GroupsPack>();
        for (Group item : bot.getGroups()) {
            GroupsPack info = new GroupsPack();
            info.id = item.getId();
            info.name = item.getName();
            info.img = item.getAvatarUrl();
            info.oid = item.getOwner().getId();
            info.oname = item.getOwner().getNameCard();
            info.per = item.getBotPermission().name();
            list.add(info);
        }
        return list;
    }

    public static List<FriendsPack> getFriends() {
        var list = new ArrayList<FriendsPack>();
        for (Friend item : bot.getFriends()) {
            FriendsPack info = new FriendsPack();
            info.id = item.getId();
            info.name = item.getNick();
            info.img = item.getAvatarUrl();
            list.add(info);
        }
        return list;
    }

    public static List<MemberInfoPack> getMembers(long id) {
        if (bot.getGroups().contains(id)) {
            var list = new ArrayList<MemberInfoPack>();
            for (var item : bot.getGroup(id).getMembers()) {
                var info = new MemberInfoPack();
                info.id = item.getId();
                info.name = item.getNameCard();
                info.img = item.getAvatarUrl();
                info.nick = item.getNick();
                info.per = item.getPermission().name();
                info.mute = item.getMuteTimeRemaining();
                list.add(info);
            }
            return list;
        } else
            return null;
    }

    public static GroupSettings getGroupInfo(long id) {
        if (bot.getGroups().contains(id)) {
            var item = bot.getGroup(id);
            return item.getSettings();
        } else
            return null;
    }

    public static void sendGroupImage(long id, String img) {
        try {
            var group = bot.getGroup(id);
            group.sendMessage(group.uploadImage(new ByteArrayInputStream(decoder.decode(img))));
        } catch (Exception e) {
            Start.logger.error("发送群图片失败", e);
        }
    }

    public static void sendGroupPrivataImage(long id, long fid, String img) {
        try {
            var member = bot.getGroup(id).get(fid);
            member.sendMessage(member.uploadImage(new ByteArrayInputStream(decoder.decode(img))));
        } catch (Exception e) {
            Start.logger.error("发送私聊图片失败", e);
        }
    }

    public static void sendFriendImage(long id, String img) {
        try {
            var friend = bot.getFriend(id);
            friend.sendMessage(friend.uploadImage(new ByteArrayInputStream(decoder.decode(img))));
        } catch (Exception e) {
            Start.logger.error("发送朋友失败", e);
        }
    }

    public static void DeleteGroupMember(long id, long fid) {
        try {
            var member = bot.getGroup(id).get(fid);
            member.kick();
        } catch (Exception e) {
            Start.logger.error("踢出成员失败", e);
        }
    }

    public static void MuteGroupMember(long id, long fid, int time) {
        try {
            var member = bot.getGroup(id).get(fid);
            member.mute(time);
        } catch (Exception e) {
            Start.logger.error("禁言成员失败", e);
        }
    }

    public static void UnmuteGroupMember(long id, long fid) {
        try {
            var member = bot.getGroup(id).get(fid);
            member.unmute();
        } catch (Exception e) {
            Start.logger.error("解禁成员失败", e);
        }
    }

    public static void GroupMuteAll(long id) {
        try {
            var group = bot.getGroup(id);
            group.getSettings().setMuteAll(true);
        } catch (Exception e) {
            Start.logger.error("全群禁言失败", e);
        }
    }

    public static void GroupUnmuteAll(long id) {
        try {
            var group = bot.getGroup(id);
            group.getSettings().setMuteAll(false);
        } catch (Exception e) {
            Start.logger.error("全群解禁失败", e);
        }
    }

    public static void SetGroupMemberCard(long id, long fid, String card) {
        try {
            var member = bot.getGroup(id).get(fid);
            member.setNameCard(card);
        } catch (Exception e) {
            Start.logger.error("修改群员名片失败", e);
        }
    }

    public static void SetGroupName(long id, String name) {
        try {
            Group group = bot.getGroup(id);
            group.setName(name);
        } catch (Exception e) {
            Start.logger.error("设置群名失败", e);
        }
    }

    public static void ReCall(Long id) {
        try {
            reList.add(id);
        } catch (Exception e) {
            Start.logger.error("消息撤回失败", e);
        }
    }

    public static void SendGroupSound(long id, String sound) {
        try {
            Group group = bot.getGroup(id);
            group.sendMessage(group.uploadVoice(new ByteArrayInputStream(decoder.decode(sound))));
        } catch (Exception e) {
            Start.logger.error("发送群语音失败", e);
        }
    }
}
