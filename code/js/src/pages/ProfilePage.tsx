import { useAuth, usePermissions } from "../contexts/AuthContext";
import { Layout } from "../components/Layout";

const ROLE_DESCRIPTIONS: Record<string, string> = {
	ADMIN: "Full access — manage users, roles, all workflows and tasks.",
	WRITER: "Writer — create, edit and delete own workflows and tasks.",
	READER: "Reader — read-only access to workflows, tasks, schedules and execution logs.",
	DEV: "Developer — read access plus workflow execution and dev tools.",
};

interface PermGroupProps {
	title: string;
	rows: Array<{ label: string; granted: boolean }>;
}

function PermGroup({ title, rows }: PermGroupProps) {
	return (
		<div className="perm-group">
			<h4 className="perm-group-title">{title}</h4>
			<ul className="permission-list">
				{rows.map((r) => (
					<li key={r.label} className={`perm-row ${r.granted ? "granted" : "denied"}`}>
						<span className="perm-icon">{r.granted ? "✅" : "🚫"}</span>
						{r.label}
					</li>
				))}
			</ul>
		</div>
	);
}

export function ProfilePage() {
	const { user } = useAuth();
	const perms = usePermissions();

	if (!user) return null;

	return (
		<Layout>
			<div className="page-header">
				<h1>Profile</h1>
			</div>

			<div className="form-card profile-card">
				<div className="profile-avatar">{user.username.charAt(0).toUpperCase()}</div>
				<div className="profile-info">
					<h2>{user.username}</h2>
					<span className={`role-badge role-${user.role.toLowerCase()}`}>{user.role}</span>
					<p className="text-muted mt-2">{ROLE_DESCRIPTIONS[user.role] ?? "Custom role"}</p>
				</div>

				<div className="profile-permissions">
					<h3>Your permissions</h3>

					<PermGroup
						title="🗂 Workflows"
						rows={[
							{ label: "Read workflows", granted: perms.canReadWorkflows },
							{ label: "Create / edit workflows", granted: perms.canWriteWorkflows },
							{ label: "Delete workflows", granted: perms.canDeleteWorkflows },
							{ label: "Execute workflows", granted: perms.canExecuteWorkflows },
						]}
					/>

					<PermGroup
						title="📋 Tasks"
						rows={[
							{ label: "Read tasks", granted: perms.canReadTasks },
							{ label: "Create / edit tasks", granted: perms.canWriteTasks },
							{ label: "Delete tasks", granted: perms.canDeleteTasks },
						]}
					/>

					<PermGroup
						title="🕒 Schedules"
						rows={[
							{ label: "Read schedules", granted: perms.canReadSchedules },
							{ label: "Create / edit schedules", granted: perms.canWriteSchedules },
							{ label: "Delete schedules", granted: perms.canDeleteSchedules },
						]}
					/>

					<PermGroup
						title="⚙️ System"
						rows={[
							{ label: "View execution history", granted: perms.canReadExecutions },
							{ label: "Admin panel", granted: perms.canAccessAdmin },
							{ label: "Dev tools", granted: perms.canAccessDev },
						]}
					/>
				</div>
			</div>
		</Layout>
	);
}
